import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implementation of the TKUS algorithm for Top-K High-Utility Sequential Pattern Mining.
 * Coded by Trinh D.D. Nguyen, adapted from the USpan codebase by Philippe Fournier-Viger.
 *
 * Based on the paper:
 *   Zhang, C., Du, Z., Gan, W., Yu, P.S.: "TKUS: Mining Top-k High-Utility Sequential
 *   Patterns." arXiv:2011.13454 (2020).
 *
 * TKUS eliminates the need for a user-defined minimum utility threshold by accepting k,
 * the desired number of top-utility patterns. It employs three strategies:
 *   - SUR  (Sequence Utility Raising)    : raises minUtil before mining begins
 *   - TDE  (Terminate Descendants Early) : depth pruning via PEU upper bound
 *   - EUI  (Eliminate Unpromising Items) : width pruning via RSU upper bound
 *
 * Data structures:
 *   SequenceData (algorithm-facing sequence view), UtilityChain (projected database),
 *   QMatrix (raw data), Dataset, MemoryLogger
 *
 * @see SequenceData
 * @see UtilityChain
 * @see Dataset
 */
public class AlgoTKUS {

    // ---- Configuration -------------------------------------------------------

    /** If true, debugging information will be shown in the console */
    final boolean DEBUG = false;

    /** If true, output is written in a human-readable format */
    final boolean SAVE_RESULT_EASIER_TO_READ_FORMAT = true;

    /** Buffer size for processing sequences */
    final int BUFFERS_SIZE = 2000;

    /** Maximum pattern length (number of items) */
    int maxPatternLength = -1; // -1 means no length constraint

    // ---- Runtime state -------------------------------------------------------

    /** Current minimum utility threshold (dynamic, raised as top-k list fills) */
    int minUtility = 0;

    /** The desired number of top-k patterns */
    int k;

    /** Pattern output buffer */
    private int[] patternBuffer;

    /** Output writer for pattern results */
    OutputResult outputResult;

    /** Number of patterns found */
    int patternCount = 0;

    /** timer for measuring execution time */
    Timer timer = new Timer();

    double memoryUsed = 0; // for tracking memory usage (in MB)

    /**
     * TKList: maintains the top-k HUSPs found so far.
     * Key   = utility (ascending order so we can find the minimum quickly)
     * Value = list of patterns with that utility (stored as int[] of pattern buffer copy)
     *
     * The total size is tracked separately so we can check when we have >= k patterns.
     */
    private TreeMap<Integer, List<int[]>> tkList = new TreeMap<>();
    private int tkListSize = 0;

    // ---- Pruning counters (for statistics) ----------------------------------
    /** Items pruned at depth 0 by PEU < minUtility (EUI at first-time pass) */
    long prunedByEUI0 = 0;
    /** I-extension candidates pruned by RSU < minUtility (EUI width pruning) */
    long prunedByEUI_I = 0;
    /** S-extension candidates pruned by RSU < minUtility (EUI width pruning) */
    long prunedByEUI_S = 0;
    /** Subtrees pruned by PEU < minUtility (TDE depth pruning) */
    long prunedByTDE = 0;

    // ---- Candidate counters (for statistics) --------------------------------
    /** 1-sequence items considered at depth 0 (before EUI0 filter) */
    long candidatesDepth0 = 0;
    /** I-extension candidates generated across all recursive calls (before EUI) */
    long candidatesI = 0;
    /** S-extension candidates generated across all recursive calls (before EUI) */
    long candidatesS = 0;

    // -------------------------------------------------------------------------

    /** Default constructor */
    public AlgoTKUS() {}

    /**
     * Set the maximum pattern length.
     * @param maxPatternLength the maximum pattern length (number of itemsets)
     */
    public void setMaxPatternLength(int maxPatternLength) {
        this.maxPatternLength = maxPatternLength;
    }

    /**
     * Run the TKUS algorithm.
     *
     * @param input  path to the input database file
     * @param output path to the output file
     * @param k      the desired number of top-k HUSPs
     * @throws IOException if error reading or writing files
     */
    public void runAlgorithm(String input, String output, int k) throws IOException {
        MemoryLogger.getInstance().reset();
        this.k = k;
        this.minUtility = 0;
        this.patternCount = 0;
        this.tkList = new TreeMap<>();
        this.tkListSize = 0;
        this.prunedByEUI0 = this.prunedByEUI_I = this.prunedByEUI_S = this.prunedByTDE = 0;
        this.candidatesDepth0 = this.candidatesI = this.candidatesS = 0;

        patternBuffer = new int[BUFFERS_SIZE];
        timer.start();
        outputResult = new OutputResult(output, SAVE_RESULT_EASIER_TO_READ_FORMAT);

        // ================================================================
        // Step 1 — Load the dataset (with minUtility = 0 so no item is
        //           filtered out before the SUR strategy sets a real threshold)
        // ================================================================
        Dataset loader = new Dataset(DEBUG, BUFFERS_SIZE);
        Dataset.DatasetResult result;
        try {
            result = loader.loadDataset(input, 0);
        } catch (Exception e) {
            e.printStackTrace();
            outputResult.close();
            return;
        }
        if (result == null) {
            System.err.println("[TKUS] Failed to load dataset.");
            outputResult.close();
            return;
        }

        List<SequenceData> database = result.sequenceDatabase;

        MemoryLogger.getInstance().checkMemory();

        // ================================================================
        // Step 2 — SUR strategy: collect utility values of all
        //           1-sequences (items), and raw q-sequence utilities.
        //           Set minUtility to the k-th highest value found.
        // ================================================================
        applySURStrategy(database, result.sequenceUtilities);

        if (DEBUG) {
            System.out.println("[TKUS] After SUR: minUtility = " + minUtility);
        }

        // ================================================================
        // Step 3 — Mine: for each 1-sequence (item) with PEU >= minUtility,
        //           recursively call the Projection-TKUS procedure (TDE check).
        // ================================================================
        tkusFirstTime(database);

        MemoryLogger.getInstance().checkMemory();

        // ================================================================
        // Step 4 — Write the final top-k patterns to the output file
        // ================================================================
        writeTopKToFile();

        outputResult.close();
        patternCount = outputResult.getPatternCount();
        timer.stop();
        memoryUsed = MemoryLogger.getInstance().getMaxMemory();
    }

    // =========================================================================
    // SUR — Sequence Utility Raising
    // =========================================================================

    /**
     * Implements the SUR strategy (Definition 11 in the paper).
     *
     * Collects the utility of every 1-sequence (item) across the database and
     * the utility of every full q-sequence. Sorts all values descending and
     * sets minUtility to the k-th highest value.
     *
     * @param database         the loaded sequence database
     * @param sequenceUtils    raw q-sequence utilities collected during loading
     */
    private void applySURStrategy(List<SequenceData> database, List<Integer> sequenceUtils) {
        // Utility of each 1-sequence (item): sum over sequences of the best
        // single-itemset occurrence of that item.
        Map<Integer, Integer> itemUtility = new HashMap<>();
        for (SequenceData seq : database) {
            for (int r = 0; r < seq.itemNames.length; r++) {
                int item = seq.itemNames[r];
                int maxU = 0;
                for (int c = 0; c < seq.matrixItemUtility[r].length; c++) {
                    if (seq.matrixItemUtility[r][c] > maxU) {
                        maxU = seq.matrixItemUtility[r][c];
                    }
                }
                Integer prev = itemUtility.get(item);
                itemUtility.put(item, (prev == null ? 0 : prev) + maxU);
            }
        }
        // Definition 11 ranks the SET of 1-, 2- and q-sequences, so a sequence
        // contributes one entry however many ways the database spells it.
        minUtility = SurThreshold.compute(database, sequenceUtils, itemUtility, k);
    }

    // =========================================================================
    // First-time pass (1-sequences)
    // =========================================================================

    /**
     * First pass: evaluates every 1-sequence (single item) and launches
     * the recursive Projection-TKUS for promising ones (TDE check on PEU).
     *
     * <p>Builds a {@link UtilityChain} for each item directly from the database
     * (Information Table + Head Table + Utility-List as in the TKUS paper).
     */
    private void tkusFirstTime(List<SequenceData> database) throws IOException {

        Map<Integer, UtilityChain> mapItemChain = new HashMap<>();

        for (SequenceData seq : database) {
            for (int r = 0; r < seq.itemNames.length; r++) {
                int item = seq.itemNames[r];
                UtilityChainEntry entry = null;
                int maxU = 0;

                for (int c = 0; c < seq.matrixItemUtility[r].length; c++) {
                    int u = seq.matrixItemUtility[r][c];
                    if (u > 0) {
                        if (entry == null) entry = new UtilityChainEntry(seq, r);
                        entry.addElement(c, u, seq.matrixItemRemainingUtility[r][c]);
                        if (u > maxU) maxU = u;
                    }
                }

                if (entry != null) {
                    UtilityChain chain = mapItemChain.computeIfAbsent(item, x -> new UtilityChain());
                    chain.addEntry(entry);
                    chain.totalUtility += maxU;
                }
            }
        }

        // Build candidate list. The depth-0 filter must use totalBound, not
        // totalPEU: an item dropped here is never emitted, so the bound has to
        // cover the 1-sequence itself. With the zero-claused totalPEU this
        // silently lost items whose every occurrence ends its sequence — on
        // Yoochoose, where 44% of positions are like that, k=10 returned 2
        // patterns instead of 10.
        List<int[]> candidates = new ArrayList<>();  // {item, totalUtility, totalPEU}
        for (Map.Entry<Integer, UtilityChain> e : mapItemChain.entrySet()) {
            int item = e.getKey();
            UtilityChain chain = e.getValue();
            candidatesDepth0++;
            if (chain.totalBound < minUtility) { prunedByEUI0++; continue; }
            candidates.add(new int[]{item, chain.totalUtility, chain.totalPEU});
        }

        // =========================================================================
        // Strategy 4 (firstTime): emit ALL depth-0 patterns BEFORE recursing.
        //
        // Sorting by utility-descending and emitting first maximises the minUtility
        // rise BEFORE any chain is projected.  The RSU re-filter below then
        // eliminates many candidates from the recursion entirely, shrinking the
        // search tree at the root — where the fan-out is largest.
        // =========================================================================

        // Phase 1: emit utility-descending to raise minUtility as high as possible.
        candidates.sort((a, b) -> b[1] - a[1]);
        for (int[] cand : candidates) {
            if (cand[1] >= minUtility) {
                patternBuffer[0] = cand[0];
                updateTKList(patternBuffer, 1, cand[1]);
            }
        }

        // Phase 2: sort PEU-descending; recurse only on re-filter survivors.
        candidates.sort((a, b) -> b[2] - a[2]);
        for (int[] cand : candidates) {
            int item = cand[0];
            int peu  = cand[2];

            if (peu < minUtility) { prunedByTDE++; continue; }

            if (maxPatternLength < 0 || 1 < maxPatternLength) {
                patternBuffer[0] = item;
                tkusProject(patternBuffer, 1, mapItemChain.get(item), item, 1);
            }
        }

        MemoryLogger.getInstance().checkMemory();
    }

    // =========================================================================
    // Projection-TKUS (recursive)
    // =========================================================================

    /** Candidate extension — carries RSU (EUI check), utility chain, and PEU (TDE check). */
    private static class CandidateInfo {
        int item;
        int utility;        // actual utility of the extended pattern
        int peu;            // PEU upper bound of the extended pattern (TDE)
        int rsu;            // RSU upper bound (EUI width pruning)
        boolean iExtension; // true = I-extension, false = S-extension
        final UtilityChain chain = new UtilityChain();
        // Per-sequence dedup for RSU accumulation: keep max parentPEU per sequence.
        int lastSeenId  = -1;
        int lastSeenRSU;

        CandidateInfo(int item, boolean iExt) {
            this.item = item;
            this.iExtension = iExt;
        }
    }

    /**
     * Recursive Projection-TKUS procedure (Algorithm 2 in the paper).
     *
     * <p>Uses the utility chain structure for the projected database.
     * The {@code lastItem} parameter is the last item
     * appended to {@code prefix} and is needed to locate the anchor row for
     * I-extension candidate scanning.
     *
     * @param prefix          current pattern buffer
     * @param prefixLength    length used in the buffer
     * @param parentChain     utility chain for the current prefix
     * @param lastItem        last item in the prefix (used for I-extension row lookup)
     * @param itemCount       number of itemsets in the prefix (for maxPatternLength guard)
     */
    private void tkusProject(int[] prefix, int prefixLength,
                             UtilityChain parentChain, int lastItem,
                             int itemCount) throws IOException {

        // ---- I-Extension RSU scan ------------------------------------------
        Map<Integer, CandidateInfo> iExtMap = new HashMap<>();

        for (UtilityChainEntry entry : parentChain.entries) {
            SequenceData seq  = entry.sequence;
            int rowLast       = entry.anchorRow;

            for (UtilityChainElement elem : entry.elements) {
                int col       = elem.itemsetID;
                int parentPEU = elem.utility + elem.restUtility;

                for (int r = rowLast + 1; r < seq.itemNames.length; r++) {
                    int u = seq.matrixItemUtility[r][col];
                    if (u > 0) {
                        int item = seq.itemNames[r];
                        CandidateInfo ci = iExtMap.computeIfAbsent(item, x -> new CandidateInfo(x, true));
                        if (ci.lastSeenId != seq.id) {
                            ci.lastSeenId  = seq.id;
                            ci.lastSeenRSU = parentPEU;
                            ci.rsu        += parentPEU;
                        } else if (parentPEU > ci.lastSeenRSU) {
                            ci.rsu        += parentPEU - ci.lastSeenRSU;
                            ci.lastSeenRSU = parentPEU;
                        }
                    }
                }
            }
        }

        // EUI pruning on I-extensions
        List<CandidateInfo> iCandidates = new ArrayList<>();
        for (CandidateInfo ci : iExtMap.values()) {
            candidatesI++;
            if (ci.rsu >= minUtility) iCandidates.add(ci);
            else prunedByEUI_I++;
        }

        // Build utility chains for surviving I-extension candidates
        for (CandidateInfo ci : iCandidates) {
            int item = ci.item;
            for (UtilityChainEntry entry : parentChain.entries) {
                SequenceData seq     = entry.sequence;
                int          rowLast = entry.anchorRow;
                Integer boxedRow = seq.itemToRow.get(item);
                if (boxedRow == null) continue;
                int rowItem = boxedRow;
                if (rowItem <= rowLast) continue;

                UtilityChainEntry newEntry = null;
                int maxU = 0;

                for (UtilityChainElement elem : entry.elements) {
                    int col = elem.itemsetID;
                    int u   = seq.matrixItemUtility[rowItem][col];
                    if (u > 0) {
                        int newU = elem.utility + u;
                        int rest = seq.matrixItemRemainingUtility[rowItem][col];
                        if (newEntry == null) newEntry = new UtilityChainEntry(seq, rowItem);
                        newEntry.addElement(col, newU, rest);
                        if (newU > maxU) maxU = newU;
                    }
                }

                if (newEntry != null) {
                    ci.chain.addEntry(newEntry);
                    ci.chain.totalUtility += maxU;
                }
            }
            ci.utility = ci.chain.totalUtility;
            ci.peu     = ci.chain.totalPEU;
        }

        // ---- S-Extension RSU scan ------------------------------------------
        Map<Integer, CandidateInfo> sExtMap = new HashMap<>();

        for (UtilityChainEntry entry : parentChain.entries) {
            SequenceData seq = entry.sequence;

            for (UtilityChainElement elem : entry.elements) {
                int col       = elem.itemsetID;
                int parentPEU = elem.utility + elem.restUtility;
                int nbCols    = seq.matrixItemUtility[0].length;

                for (int r = 0; r < seq.itemNames.length; r++) {
                    if (seq.nextPos[r][col + 1] < nbCols) {
                        int item = seq.itemNames[r];
                        CandidateInfo ci = sExtMap.computeIfAbsent(item, x -> new CandidateInfo(x, false));
                        if (ci.lastSeenId != seq.id) {
                            ci.lastSeenId  = seq.id;
                            ci.lastSeenRSU = parentPEU;
                            ci.rsu        += parentPEU;
                        } else if (parentPEU > ci.lastSeenRSU) {
                            ci.rsu        += parentPEU - ci.lastSeenRSU;
                            ci.lastSeenRSU = parentPEU;
                        }
                    }
                }
            }
        }

        // EUI pruning on S-extensions
        List<CandidateInfo> sCandidates = new ArrayList<>();
        for (CandidateInfo ci : sExtMap.values()) {
            candidatesS++;
            if (ci.rsu >= minUtility) sCandidates.add(ci);
            else prunedByEUI_S++;
        }

        // Build utility chains for surviving S-extension candidates
        for (CandidateInfo ci : sCandidates) {
            int item = ci.item;
            for (UtilityChainEntry entry : parentChain.entries) {
                SequenceData seq = entry.sequence;
                Integer boxedRow = seq.itemToRow.get(item);
                if (boxedRow == null) continue;
                int rowItem = boxedRow;
                int nbCols  = seq.matrixItemUtility[rowItem].length;

                UtilityChainEntry newEntry = null;
                int maxU = 0;

                for (UtilityChainElement elem : entry.elements) {
                    int col   = elem.itemsetID;
                    // Every occurrence after col, not just the first: the utility of
                    // a sequence at a q-sequence is the MAXIMUM over its positions,
                    // so stopping at the first position can return a smaller value
                    // than the maximum it is supposed to be.
                    for (int nextC = seq.nextPos[rowItem][col + 1]; nextC < nbCols;
                             nextC = seq.nextPos[rowItem][nextC + 1]) {
                        int u    = seq.matrixItemUtility[rowItem][nextC];
                        int newU = elem.utility + u;
                        int rest = seq.matrixItemRemainingUtility[rowItem][nextC];
                        if (newEntry == null) newEntry = new UtilityChainEntry(seq, rowItem);
                        newEntry.addElement(nextC, newU, rest);
                        if (newU > maxU) maxU = newU;
                    }
                }

                if (newEntry != null) {
                    ci.chain.addEntry(newEntry);
                    ci.chain.totalUtility += maxU;
                }
            }
            ci.utility = ci.chain.totalUtility;
            ci.peu     = ci.chain.totalPEU;
        }

        // =========================================================================
        // Strategy 4-TKUS: emit ALL candidates utility-descending BEFORE recursing.
        // This raises minUtility as high as possible so the RSU re-filter that
        // follows eliminates more candidates from the recursion.
        // =========================================================================

        List<CandidateInfo> allCandidates = new ArrayList<>(iCandidates.size() + sCandidates.size());
        allCandidates.addAll(iCandidates);
        allCandidates.addAll(sCandidates);
        allCandidates.sort((a, b) -> b.utility - a.utility);

        for (CandidateInfo ci : allCandidates) {
            if (ci.iExtension) {
                if (ci.utility >= minUtility) {
                    prefix[prefixLength] = ci.item;
                    updateTKList(prefix, prefixLength + 1, ci.utility);
                }
            } else {
                if (ci.utility >= minUtility && (maxPatternLength < 0 || itemCount + 1 <= maxPatternLength)) {
                    prefix[prefixLength]     = -1;
                    prefix[prefixLength + 1] = ci.item;
                    updateTKList(prefix, prefixLength + 2, ci.utility);
                }
            }
        }

        // RSU re-filter with the now-raised minUtility → smaller seqList.
        List<CandidateInfo> seqList = new ArrayList<>();
        for (CandidateInfo ci : allCandidates) {
            if (ci.rsu >= minUtility) seqList.add(ci);
        }

        // Sort by PEU descending (paper Alg.2 line 26) to raise minUtility faster.
        seqList.sort((a, b) -> b.peu - a.peu);

        // ---- Recurse (TDE check: PEU >= minUtility) ----------------------------------------
        for (CandidateInfo ci : seqList) {
            if (ci.peu < minUtility) { prunedByTDE++; continue; }
            if (ci.chain.entries.isEmpty()) continue;

            if (ci.iExtension) {
                prefix[prefixLength] = ci.item;
                tkusProject(prefix, prefixLength + 1, ci.chain, ci.item, itemCount);
            } else {
                prefix[prefixLength]     = -1;
                prefix[prefixLength + 1] = ci.item;
                if (maxPatternLength < 0 || itemCount + 1 <= maxPatternLength) {
                    tkusProject(prefix, prefixLength + 2, ci.chain, ci.item, itemCount + 1);
                }
            }
        }

        MemoryLogger.getInstance().checkMemory();
    }

    // =========================================================================
    // TKList management
    // =========================================================================

    /**
     * Update the top-k list with a new pattern.
     *
     * Per Definition 7 of the TKUS paper, a sequence s is a top-k HUSP if there
     * exist FEWER THAN k sequences with strictly HIGHER utility. This means all
     * patterns tied at the minimum utility level must be kept together — we never
     * partially remove a utility bucket. We only drop an entire bucket when there
     * are already >= k patterns with strictly higher utility.
     *
     * @param prefix        the pattern buffer
     * @param prefixLength  number of elements used in the buffer
     * @param utility       utility of the pattern
     */
    private void updateTKList(int[] prefix, int prefixLength, int utility) {
        // Copy the pattern into a new array so the buffer can be reused
        int[] pattern = Arrays.copyOf(prefix, prefixLength);

        // Add to tkList under the appropriate utility bucket
        tkList.computeIfAbsent(utility, u -> new ArrayList<>()).add(pattern);
        tkListSize++;

        // Drop the minimum-utility bucket ONLY when there are already >= k patterns
        // with strictly higher utility (so the entire bucket becomes non-top-k).
        // Never partially remove a bucket — all patterns at the same utility level
        // share the same rank and must be treated together (Definition 7).
        while (!tkList.isEmpty()) {
            int minKey = tkList.firstKey();
            List<int[]> minBucket = tkList.get(minKey);
            int countAboveMin = tkListSize - minBucket.size();
            if (countAboveMin >= k) {
                // There are already k or more patterns with higher utility,
                // so none of the patterns in this bucket qualify as top-k HUSPs.
                tkListSize -= minBucket.size();
                tkList.remove(minKey);
            } else {
                break;
            }
        }

        // Raise minUtility to the current lowest utility still in the top-k list
        if (tkListSize >= k && !tkList.isEmpty()) {
            minUtility = tkList.firstKey();
        }

        if (DEBUG) {
            System.out.println("[TKUS] TKList updated: size=" + tkListSize + " minUtil=" + minUtility
                + " pattern=" + patternToString(pattern, pattern.length) + " util=" + utility);
        }
    }

    // =========================================================================
    // Output
    // =========================================================================

    /** Write all top-k patterns collected in tkList to the output file. */
    private void writeTopKToFile() throws IOException {
        // Iterate descending by utility
        for (Map.Entry<Integer, List<int[]>> e : tkList.descendingMap().entrySet()) {
            int utility = e.getKey();
            for (int[] pattern : e.getValue()) {
                outputResult.writePattern(pattern, pattern.length, utility);
            }
        }
    }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    private String patternToString(int[] pattern, int length) {
        // Reuse OutputResult formatting (utility=0 as placeholder, strip the ":0" suffix)
        String s = OutputResult.formatStatic(pattern, length, -1, true);
        return s.substring(0, s.lastIndexOf(":-1"));
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /** Print statistics to the console */
    public void printStats() {
        System.out.println("============= " + ANSI.CORAL + "TKUS ALGORITHM" + ANSI.RESET + " STATISTICS =============");
        System.out.println(" Total time     ~ " + ANSI.GREEN  + timer.formatElapsed(Timer.TimeUnit.MILLISECONDS)
                           + " / " + timer.formatElapsed(Timer.TimeUnit.SECONDS) + ANSI.RESET);
        System.out.println(" Memory (peak)  ~ " + ANSI.LIME   + MemoryLogger.formatMemory((long) memoryUsed, MemoryLogger.MemoryUnit.MB) + ANSI.RESET);
        System.out.println(" Patterns found : " + ANSI.CANDY  + patternCount  + ANSI.RESET);
        System.out.println(" minUtility     : " + ANSI.ORANGE + minUtility    + ANSI.RESET);
        System.out.println(" --- Pruning stats ---");
        System.out.println(" Pruned by EUI (depth 0)   : " + ANSI.CYAN   + prunedByEUI0   + ANSI.RESET);
        System.out.println(" Pruned by EUI (I-ext RSU) : " + ANSI.CYAN   + prunedByEUI_I  + ANSI.RESET);
        System.out.println(" Pruned by EUI (S-ext RSU) : " + ANSI.CYAN   + prunedByEUI_S  + ANSI.RESET);
        System.out.println(" Pruned by TDE (PEU)       : " + ANSI.YELLOW + prunedByTDE    + ANSI.RESET);
        System.out.println(" Candidates pruned (total) : " + ANSI.RED    + (prunedByEUI0 + prunedByEUI_I + prunedByEUI_S + prunedByTDE) + ANSI.RESET);
        System.out.println(" --- Candidate stats ---");
        System.out.println(" Candidates (depth 0)      : " + ANSI.WHITE  + candidatesDepth0 + ANSI.RESET);
        System.out.println(" Candidates I-ext (total)  : " + ANSI.WHITE  + candidatesI      + ANSI.RESET);
        System.out.println(" Candidates S-ext (total)  : " + ANSI.WHITE  + candidatesS      + ANSI.RESET);
        System.out.println(" Candidates total          : " + ANSI.WHITE  + (candidatesDepth0 + candidatesI + candidatesS) + ANSI.RESET);
        System.out.println("======================================================");
    }

    public void logo() {
        // Gradient: white -> yellow -> red -> brown
        int[][] g = {{255,255,255}, {255,220,0}, {220,20,20}, {130,70,20}};
        System.out.println(ANSI.gradientLine("_____._.____/\\ .____     .________", g));
        System.out.println(ANSI.gradientLine("\\__ _:|:   /  \\|    |___ |    ___/", g));
        System.out.println(ANSI.gradientLine("  |  :||.  ___/|    |   ||___    \\", g));
        System.out.println(ANSI.gradientLine("  |   ||     \\ |    :   ||       /", g));
        System.out.println(ANSI.gradientLine("  |   ||      \\|        ||__:___/ ", g));
        System.out.println(ANSI.gradientLine("  |___||___\\  /|. _____/    :     ", g));
        System.out.println(ANSI.gradientLine("            \\/  :/                ", g));
        System.out.println(ANSI.gradientLine("                :                 ", g));
    }

    // =========================================================================
    // Optional: entry point for standalone testing
    // =========================================================================

    public static void main(String[] args) throws IOException {
		String input = "../datasets/HUSRM.txt";
        String output = "../outputs/tkus.txt";
        int k = 12;
        int loops = 1; // run multiple times to check stability of timing and memory stats

        System.out.println("Database : " + ANSI.AMETHYST + input + ANSI.RESET);
        System.out.println("Output   : " + ANSI.DENIM + output + ANSI.RESET);
        System.out.println("K        : " + ANSI.NAVY + k + ANSI.RESET);
        System.out.println();

        Timer minTimer = new Timer();
        double minMemory = Long.MAX_VALUE;
        for (int i = 0; i < loops; i++) {
            System.out.println(ANSI.BOLD + ANSI.BLUE + "=== RUN " + (i + 1) + "/" + loops + " ===" + ANSI.RESET);
            AlgoTKUS algo = new AlgoTKUS();
            algo.logo();
            algo.runAlgorithm(input, output, k);
            algo.printStats();
            if (algo.timer.getElapsed(Timer.TimeUnit.SECONDS) < minTimer.getElapsed(Timer.TimeUnit.SECONDS) || i == 0) {
                minTimer = algo.timer;
            }
            if (algo.memoryUsed < minMemory || i == 0) {
                minMemory = algo.memoryUsed;
            }
        }
        System.out.println("Best runtime     : " + ANSI.BOLD + ANSI.ORANGE + minTimer.formatElapsed(Timer.TimeUnit.SECONDS) + ANSI.RESET + " seconds");
        System.out.println("Best memory usage: " + ANSI.BOLD + ANSI.BRIGHT_YELLOW + MemoryLogger.formatMemory((long) minMemory, MemoryLogger.MemoryUnit.MB) + ANSI.RESET + " MB");
    }
}
