import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * AlgoTKCM — unified top-k HUSP mining engine with per-bound ablation flags.
 *
 * <p>Every configuration runs on the SAME substrate (utility chains, object
 * pools, entry-first chain build, two-phase emission, sequence ordering), so
 * wall-clock differences between configurations are attributable to the bound
 * flags alone (editorial rule 2.3.4). Each flag changes exactly one factor
 * (rule 2.3.7) and never changes the result set (rule 2.3.8) — enforced by
 * test.sh, which verifies all 8 flag combinations against exhaustive USpan
 * enumeration.</p>
 *
 * <h3>Flags</h3>
 * <ul>
 *   <li>{@code tightWidth} (w): width-pruning bound for extension candidates.
 *       {@code false} = RSU as in TKUS (parent PEU credited per sequence where
 *       the extension exists). {@code true} = TRSU (the child pattern's own
 *       combined PEU: per-sequence max of {@code newU + rest}, summed).
 *       TRSU ≤ RSU always.</li>
 *   <li>{@code splitSGate} (s): depth gate for the S-extension subtree.
 *       {@code false} = the child's combined PEU (TKUS's TDE). {@code true} =
 *       S-PEU ({@code newU + interRemaining}), which drops the same-column
 *       tail and is sound because every S-descendant lies in a later column.</li>
 *   <li>{@code useCupt} (c): co-occurrence pre-filter table. Pure filter —
 *       never changes the result set, only skips work.</li>
 * </ul>
 *
 * <p>The I-extension subtree is ALWAYS gated by the child's combined PEU:
 * an intra-only bound is refuted (see document.md's soundness revision and
 * {@code test/data/ipeu_counterexample.txt}).</p>
 *
 * <h3>Uniform counters (identical semantics in every configuration)</h3>
 * <ul>
 *   <li>{@code candDepth0/candI/candS} — candidates MATERIALIZED for width
 *       evaluation (a CandidateInfo created). With CUPT on, occurrences it
 *       skips never materialize — that reduction is CUPT's measured effect.</li>
 *   <li>{@code prunedWidthI/S} — materialized candidates removed by the width
 *       bound.</li>
 *   <li>{@code prunedGateI/S} — subtree sides not explored because the gate
 *       fell below minUtility (early-break counts the whole sorted tail).</li>
 *   <li>{@code cuptOccSkips} — occurrence-level work units skipped by CUPT
 *       (NOT comparable to candidate counts; report separately).</li>
 *   <li>{@code projections} — number of recursive projection calls.</li>
 *   <li>{@code chainElements/chainEntries} — utility-list rows and head-table
 *       rows materialized, cumulative; {@code peakChainElements} is the largest
 *       number of utility-list rows alive at one instant. These are the
 *       machine-invariant size of the chain representation (rule 2.5.5) and the
 *       only counters that separate {@code m0} from {@code m1}: the merge
 *       changes no candidate and no bound, so every other counter is identical
 *       between the two by construction.</li>
 *   <li>{@code scanStepsI/scanStepsS} — candidate-occurrence updates during
 *       projection (one per {@code CandidateInfo.see}), split by extension type.
 *       The merge touches the S-scan ONLY, so the two must be reported
 *       separately: a combined figure is not comparable to
 *       {@code dominatedPairs}. This is the work volume rule 2.3.9 asks to be
 *       reported wherever runtime cannot separate the configurations.</li>
 *   <li>{@code dominatedPairs} — meaningful under {@code m1} only: the number of
 *       (parent element, occurrence column) pairs a pairwise S-scan would visit
 *       ON THE SAME PARENT CHAINS. {@code p̄ = dominatedPairs / scanStepsS} is
 *       the LOCAL redundancy factor of the utility-chain representation.
 *       <p>It does NOT predict the pairwise run's total: under {@code m0} the
 *       chains below depth 1 are strictly larger, so the redundancy compounds
 *       and {@code scanStepsS(m0) >= dominatedPairs(m1)}, with equality iff
 *       {@code p̄ = 1} everywhere. Measured on BIBLE k=20: p̄ = 1.83 locally,
 *       24.25x globally. See {@code theory/column-merged-chains.md}a,
 *       Proposition A and Corollary B — an earlier draft claimed the global
 *       identity and was refuted by measurement.</p>
 *       <p>Use: {@code p̄ = 1} (or {@code scanStepsS = 0}) certifies from a
 *       single fast run that merging is inert on this input.</p></li>
 * </ul>
 *
 * <p>Baseline configuration {@code w0 s0 c0} is the TKUS strategy set (SUR +
 * TDE via PEU + EUI via RSU) on this substrate; {@code w1 s1 c1} is the full
 * enhanced configuration.</p>
 */
public class AlgoTKCM {

    // ---- Configuration -------------------------------------------------------

    final boolean tightWidth;   // w: RSU (false) vs TRSU (true)
    final boolean splitSGate;   // s: combined PEU (false) vs S-PEU (true)
    final boolean useCupt;      // c: CUPT pre-filter on/off
    /**
     * m: chain representation. false = pairwise chains (one element per
     * (parent element × occurrence) pair — TKUS/USpan lineage). true =
     * column-merged chains: one element per occurrence COLUMN carrying
     * val(c) = max over parent elements before c of (parent val) + u(item, c),
     * built with a two-pointer merge in O(|parent elements| + |occurrence
     * columns|) per (entry, row) instead of the pairwise product.
     * EXACT: utilities are the same Bellman maxima and every (val + rest)
     * bound value is preserved (dropped pairs are dominated), so the search
     * tree, all candidate counters, and the output are identical — only
     * memory and time change. Verified bit-identical by test.sh.
     */
    final boolean mergedChains; // m: pairwise (false) vs column-merged (true)
    /**
     * f: least-fixpoint item filter. When set, after SUR fixes the initial
     * threshold the vocabulary is reduced to the fixpoint of
     * {@code {a : SWU_F(a) >= theta}} and the database is rebuilt around the
     * survivors, so whole 1-item subtrees disappear before mining starts.
     * Unlike the other flags this one changes the number of projections —
     * see {@link ItemFixpoint} for the soundness argument.
     */
    final boolean fixpointItems;
    /**
     * r: iterate the fixpoint DURING mining. With {@code f} on, the vocabulary
     * is filtered once at the SUR threshold; mining then raises minUtility past
     * it, and since the operator is monotone in θ a smaller least fixpoint
     * exists. When set, the fixpoint is recomputed as the threshold ascends and
     * the items that fall out are retired in place — no reload, no work
     * discarded. Ignored when {@code f} is off, since there is no fixpoint to
     * iterate; {@code f0r1} and {@code f0r0} are the same configuration.
     */
    final boolean iterateFixpoint;

    final boolean SAVE_RESULT_EASIER_TO_READ_FORMAT = true;
    final int BUFFERS_SIZE = 2000;
    int maxPatternLength = -1;

    // ---- Runtime state -------------------------------------------------------

    int minUtility = 0;
    int k;
    private int[] patternBuffer;
    OutputResult outputResult;
    int patternCount = 0;
    Timer timer = new Timer();
    double memoryUsed = 0;

    private TreeMap<Integer, List<int[]>> tkList = new TreeMap<>();
    private int tkListSize = 0;

    // ---- Uniform counters ----------------------------------------------------

    long candDepth0 = 0, candI = 0, candS = 0;
    long prunedWidthI = 0, prunedWidthS = 0;
    long prunedGateI = 0, prunedGateS = 0;
    long cuptOccSkips = 0;
    long projections = 0;
    /** Vocabulary before and after the fixpoint filter, and rounds taken. */
    long vocabBefore = 0, vocabAfter = 0, fixpointRounds = 0;
    /** Chain representation size and scan work — see the class comment. */
    long chainElements = 0, chainEntries = 0;
    /** Candidate-occurrence updates, split by extension type: the merge
     *  touches the S-scan only, so a combined figure cannot be compared
     *  against dominatedPairs. */
    long scanStepsI = 0, scanStepsS = 0;
    /**
     * Parent-element/occurrence pairs a pairwise S-scan would visit on the SAME
     * parent chains, counted during a MERGED run (0 when the merge is off,
     * where scanStepsS already is that count). pbar = dominatedPairs/scanStepsS
     * is the LOCAL redundancy factor: 1 means nothing merges and the merged
     * form is pure overhead. It is a lower bound on the global benefit, not an
     * estimate of it — see the class comment.
     */
    long dominatedPairs = 0;
    long peakChainElements = 0;
    private long liveChainElements = 0;

    /**
     * Per-pattern-length breakdown of the two quantities above, so that
     * {@code p̄} can be read off level by level rather than only in aggregate.
     * This is what decides whether {@code p̄} measured cheaply near the root
     * predicts {@code p̄} deeper down — the precondition for choosing the chain
     * representation adaptively before mining starts.
     */
    // WITHDRAWN 2026-08-17. A flag once chose the chain representation from a
    // bounded probe. It was removed after p11 measured what it cost against what
    // it saved: the probe took 19 to 51 percent of a run, so the selecting
    // configuration was 1.23x to 2.09x slower than simply using the better arm,
    // in every one of eight cells -- including the ones it chose correctly.
    //
    // The measurement that settled it was not the probe's cost but the cost of
    // not asking at all. Merged chains are at worst 1.056x pairwise where they
    // do not help, which is inside one standard deviation on that cell, and
    // 0.14x to 0.23x where they do. A decision rule is worth its cost only when
    // choosing wrong is expensive, and here choosing merged unconditionally is
    // the whole downside. The representation is now always merged.
    //
    // p-bar keeps its role and changes it: it predicts the SIZE of the win, it
    // does not decide whether to take it.
    static final int DEPTH_BUCKETS = 24;
    long[] scanStepsSByDepth   = new long[DEPTH_BUCKETS];
    long[] dominatedPairsByDepth = new long[DEPTH_BUCKETS];

    /** SUR threshold actually used; how often the vocabulary was recomputed
     *  mid-run; and how many items that retired. */
    long surTheta = 0, retightenings = 0, itemsRetired = 0;

    /**
     * Threshold the vocabulary was last filtered at, and the threshold at which
     * it is worth filtering again.
     *
     * <p>SUR only fixes the threshold the vocabulary is FIRST built at; mining
     * then raises minUtility past it. Since {@code F ↦ {a : SWU_F(a) ≥ θ}} is
     * monotone in θ, the higher threshold admits a strictly smaller least
     * fixpoint — so the filter is re-run during mining and the items that fall
     * out are RETIRED IN PLACE.</p>
     *
     * <p>Retiring rather than restarting is the point. An earlier design
     * abandoned the enumeration and rebuilt the database over the smaller
     * vocabulary; that throws away every projection completed so far to buy the
     * same reduction. Retirement keeps all of it: an item {@code a} with
     * {@code SWU_F(a) < minUtility} appears in no pattern of utility ≥
     * minUtility (the SWU argument in {@link ItemFixpoint}), and minUtility only
     * ever rises, so skipping {@code a} in every subsequent extension — and
     * abandoning any subtree whose prefix already contains it — loses nothing.
     * Patterns already emitted are unaffected.</p>
     */
    private int vocabTheta = 0, nextCheckTheta = Integer.MAX_VALUE;
    private boolean retightenPending = false;
    /** Items retired mid-run; indexed by item id. Null when flag f is off. */
    private boolean[] retired;
    /** Database currently being mined, for the mid-run fixpoint recomputation. */
    private List<SequenceData> currentDb;

    /**
     * How far minUtility must rise above the threshold the vocabulary was last
     * filtered at before the fixpoint is recomputed. The recomputation is a few
     * passes over the database, so it must not run on every threshold bump; but
     * it is cheap enough that this can be far below the factor a restart would
     * have needed.
     */
    static final double RECHECK_FACTOR = 1.15;

    /**
     * One utility-list row was materialized. Kept separate from the live count
     * so that {@code chainElements} measures allocation volume while
     * {@code peakChainElements} measures the size of the structure that has to
     * be resident at once.
     */
    private void countElement() {
        chainElements++;
        if (++liveChainElements > peakChainElements) peakChainElements = liveChainElements;
    }

    int maxItemId = 0;
    /** SUR threshold from the unfiltered database; a valid floor after filtering. */
    private int surFloor = 0;

    // ---- Pre-allocated buffers / pools (shared substrate) --------------------

    private CandidateInfo[] dispatchI;
    private CandidateInfo[] dispatchS;
    private UtilityChainEntry[] pendingEntryByItem;
    private int[] pendingEntryDirty;
    private final Deque<UtilityChain> chainPool = new ArrayDeque<>();
    private final Deque<UtilityChainEntry> entryPool = new ArrayDeque<>();

    public AlgoTKCM(boolean tightWidth, boolean splitSGate, boolean useCupt) {
        this(tightWidth, splitSGate, useCupt, false, false);
    }

    public AlgoTKCM(boolean tightWidth, boolean splitSGate, boolean useCupt, boolean mergedChains) {
        this(tightWidth, splitSGate, useCupt, mergedChains, false);
    }

    public AlgoTKCM(boolean tightWidth, boolean splitSGate, boolean useCupt,
                     boolean mergedChains, boolean fixpointItems) {
        this(tightWidth, splitSGate, useCupt, mergedChains, fixpointItems, false);
    }

    public AlgoTKCM(boolean tightWidth, boolean splitSGate, boolean useCupt,
                     boolean mergedChains, boolean fixpointItems,
                     boolean iterateFixpoint) {
        this.tightWidth      = tightWidth;
        this.splitSGate      = splitSGate;
        this.useCupt         = useCupt;
        this.mergedChains    = mergedChains;
        this.fixpointItems   = fixpointItems;
        this.iterateFixpoint = iterateFixpoint;
    }

    /** Config label, e.g. "w1s0c1m1f1". */
    public String configName() {
        return "w" + (tightWidth ? 1 : 0) + "s" + (splitSGate ? 1 : 0)
             + "c" + (useCupt ? 1 : 0) + "m" + (mergedChains ? 1 : 0)
             + "f" + (fixpointItems ? 1 : 0) + "r" + (iterateFixpoint ? 1 : 0)
             ;
    }

    public void setMaxPatternLength(int maxPatternLength) { this.maxPatternLength = maxPatternLength; }

    /**
     * Decide the chain representation from a bounded probe, then set the flag.
     *
     * <p>The probe is an ordinary run of the same configuration truncated to
     * prefixes of length {@link #SELECT_PROBE_LEN}, always in the merged form.
     * The merged form is required, not merely convenient: under the pairwise
     * form the depth-1 buckets count the inflated scan itself, so the ratio
     * they report is 1 by construction and the probe could never select
     * merging.</p>
     *
     * <p>The probe's own output file is written beside the caller's and deleted
     * afterwards; nothing it produces reaches the caller.</p>
     */

    public void runAlgorithm(String input, String output, int k) throws IOException {
        MemoryLogger.getInstance().reset();
        this.k = k;
        this.minUtility = 0;
        this.patternCount = 0;
        this.tkList = new TreeMap<>();
        this.tkListSize = 0;
        candDepth0 = candI = candS = 0;
        prunedWidthI = prunedWidthS = prunedGateI = prunedGateS = 0;
        cuptOccSkips = 0; projections = 0;
        vocabBefore = vocabAfter = fixpointRounds = 0;
        chainElements = chainEntries = 0;
        scanStepsI = scanStepsS = dominatedPairs = 0;
        peakChainElements = liveChainElements = 0;
        java.util.Arrays.fill(scanStepsSByDepth, 0);
        java.util.Arrays.fill(dominatedPairsByDepth, 0);
        surTheta = retightenings = itemsRetired = 0;
        vocabTheta = 0; nextCheckTheta = Integer.MAX_VALUE;
        retightenPending = false; retired = null; currentDb = null;
        surFloor = 0;

        patternBuffer = new int[BUFFERS_SIZE];
        timer.start();

        outputResult = new OutputResult(output, SAVE_RESULT_EASIER_TO_READ_FORMAT);

        Dataset loader = new Dataset(false, BUFFERS_SIZE);
        Dataset.DatasetResult result;
        try {
            result = loader.loadDataset(input, 0);
        } catch (Exception e) {
            e.printStackTrace();
            outputResult.close();
            return;
        }
        if (result == null) {
            System.err.println("[AlgoTKCM] Failed to load dataset.");
            outputResult.close();
            return;
        }

        List<SequenceData> database = result.sequenceDatabase;
        MemoryLogger.getInstance().checkMemory();

        // ---- Flag f: reduce the vocabulary to a least fixpoint, then rebuild.
        // SUR has to run first because the fixpoint needs a threshold, so the
        // file is read a second time when the vocabulary actually shrinks.
        // That second read is charged to the measured runtime.
        //
        // The filter is applied ITERATIVELY. SUR only fixes the threshold the
        // vocabulary is first built at; mining then raises minUtility well past
        // it, and since a ↦ {a : SWU_F(a) ≥ θ} is monotone in θ, that higher
        // threshold admits a strictly smaller least fixpoint. When minUtility
        // has risen by RESTART_FACTOR the enumeration is abandoned and redone
        // over the smaller vocabulary.
        //
        // Exactness of the restart. At the moment of the restart, minUtility is
        // the k-th best utility found so far, hence a lower bound on the final
        // threshold. The fixpoint at that value removes only items that cannot
        // occur in any pattern of utility ≥ minUtility, so no member of the
        // final top-k is removed. The TKList is cleared and minUtility is kept
        // as a FLOOR (surFloor), so the re-enumeration is complete above it and
        // no pattern is counted twice — which re-using the old TKList would do,
        // and which would raise the threshold past patterns that belong.
        //
        // Work discarded by a restart is real work and stays in the counters
        // and in the measured runtime (rule 2.5.3): nothing here is free.
        if (fixpointItems) {
            surTheta = SurThreshold.compute(database, result.sequenceUtilities,
                                            itemUtilities(database), k);
            vocabTheta = (int) surTheta;
        }

        if (fixpointItems) {
            ItemFixpoint.Result fp = ItemFixpoint.compute(database, vocabTheta);
            vocabBefore    = fp.initialItems;
            vocabAfter     = fp.items.size();
            fixpointRounds = fp.rounds;
            surFloor       = vocabTheta;
            minUtility     = Math.max(minUtility, vocabTheta);
            if (fp.items.isEmpty()) {
                writeTopKToFile();
                outputResult.close();
                patternCount = outputResult.getPatternCount();
                timer.stop();
                memoryUsed = MemoryLogger.getInstance().getMaxMemory();
                return;
            }
            if (fp.items.size() < fp.initialItems) {
                try {
                    result = loader.loadDataset(input, 0, fp.items);
                } catch (Exception e) {
                    e.printStackTrace();
                    outputResult.close();
                    return;
                }
                database = result.sequenceDatabase;
            }
        }

        // Substrate strategy (uniform): richest sequences first so the TKList
        // fills early and minUtility rises fast. Pure reordering — exact.
        database.sort((a, b) -> b.swu - a.swu);

        for (int id : result.mapItemToSWU.keySet()) { if (id > maxItemId) maxItemId = id; }
        dispatchI          = new CandidateInfo[maxItemId + 1];
        dispatchS          = new CandidateInfo[maxItemId + 1];
        pendingEntryByItem = new UtilityChainEntry[maxItemId + 1];
        pendingEntryDirty  = new int[maxItemId + 1];
        if (fixpointItems && iterateFixpoint) {
            retired  = new boolean[maxItemId + 1];
            currentDb = database;
            nextCheckTheta = (int) Math.min(Integer.MAX_VALUE,
                    Math.ceil(Math.max(1, vocabTheta) * RECHECK_FACTOR));
        }

        mineFirstTime(database, result.sequenceUtilities);

        MemoryLogger.getInstance().checkMemory();
        writeTopKToFile();
        outputResult.close();
        patternCount = outputResult.getPatternCount();
        timer.stop();
        memoryUsed = MemoryLogger.getInstance().getMaxMemory();
    }

    // =========================================================================
    // Object-pool helper
    // =========================================================================

    /**
     * Recompute the least fixpoint at the current threshold and retire whatever
     * fell out. Exact: an item outside the fixpoint at theta = minUtility
     * occurs in no pattern of utility >= minUtility, and minUtility never
     * decreases, so no pattern that belongs in the answer is lost.
     *
     * <p>Iterating from the CURRENT (already reduced) database rather than the
     * original one is equivalent and cheaper: the fixpoint at a higher theta is
     * contained in the fixpoint at a lower one, and the operator is monotone,
     * so starting the iteration from the smaller set converges to the same
     * limit.</p>
     */
    private void retighten() {
        retightenPending = false;
        vocabTheta = minUtility;
        nextCheckTheta = (int) Math.min(Integer.MAX_VALUE,
                Math.ceil(Math.max(1, (long) vocabTheta) * RECHECK_FACTOR));
        retightenings++;
        ItemFixpoint.Result fp = ItemFixpoint.compute(currentDb, vocabTheta);
        fixpointRounds += fp.rounds;
        for (SequenceData seq : currentDb) {
            for (int item : seq.itemNames) {
                if (item <= maxItemId && !retired[item] && !fp.items.contains(item)) {
                    retired[item] = true;
                    itemsRetired++;
                }
            }
        }
        vocabAfter = fp.items.size();
    }

    private void returnChainToPool(UtilityChain chain) {
        for (UtilityChainEntry e : chain.entries) {
            liveChainElements -= e.elements.size();
            e.elements.clear();
            e.peu = 0;
            entryPool.push(e);
        }
        chain.entries.clear();
        chain.totalPEU     = 0;
        chain.totalUtility = 0;
        chainPool.push(chain);
    }

    // =========================================================================
    // CUPT (flag c) — table as specified in document.md
    // =========================================================================

    private static class CuptTable {
        final int[][] table;
        final int[]   itemToIdx;
        CuptTable(int[][] table, int[] itemToIdx) { this.table = table; this.itemToIdx = itemToIdx; }
        boolean allows(int a, int b, int threshold) {
            int ia = (a < itemToIdx.length) ? itemToIdx[a] : -1;
            int ib = (b < itemToIdx.length) ? itemToIdx[b] : -1;
            if (ia < 0 || ib < 0) return true;
            return table[ia][ib] >= threshold;
        }
    }

    private CuptTable computeCUPT(List<SequenceData> database, boolean[] promisingItems) {
        List<Integer> items = new ArrayList<>();
        for (int id = 0; id < promisingItems.length; id++) if (promisingItems[id]) items.add(id);
        int n = items.size();
        if (n == 0) return null;

        int[] itemToIdx = new int[maxItemId + 1];
        Arrays.fill(itemToIdx, -1);
        for (int i = 0; i < n; i++) itemToIdx[items.get(i)] = i;

        int[][] table = new int[n][n];
        for (SequenceData seq : database) {
            int[] names   = seq.itemNames;
            int   nbItems = names.length;
            // SOUNDNESS FIX (2026-07-31): do NOT skip single-distinct-item
            // sequences. A sequence like "a[..] -1 a[..]" supports the
            // self-pair (a, a); skipping it under-counts CUPT(a, a) and
            // over-prunes patterns of the form <(a)(a)...>. Caught on
            // Yoochoose by the output_sha256 pipeline check: true
            // CUPT(853072,853072)=4,641,436 vs 1,505,233 with the skip —
            // the top-100 pattern <(853072)(853072)>:3,729,117 was lost.
            // Regression: test/data/cupt_selfpair_counterexample.txt.
            int   nbCols  = seq.matrixItemUtility[0].length;
            int   swu     = seq.swu;

            int[] firstCol = new int[nbItems];
            int[] lastCol  = new int[nbItems];
            Arrays.fill(firstCol, Integer.MAX_VALUE);
            Arrays.fill(lastCol, -1);
            for (int r = 0; r < nbItems; r++)
                for (int c = 0; c < nbCols; c++)
                    if (seq.matrixItemUtility[r][c] > 0) {
                        if (c < firstCol[r]) firstCol[r] = c;
                        if (c > lastCol[r])  lastCol[r]  = c;
                    }

            for (int ra = 0; ra < nbItems; ra++) {
                if (firstCol[ra] == Integer.MAX_VALUE) continue;
                int ia = itemToIdx[names[ra]];
                if (ia < 0) continue;
                int fa = firstCol[ra];
                for (int rb = 0; rb < nbItems; rb++) {
                    if (lastCol[rb] <= fa) continue;
                    int ib = itemToIdx[names[rb]];
                    if (ib < 0) continue;
                    table[ia][ib] += swu;
                }
            }
        }
        for (int ia = 0; ia < n; ia++)
            for (int ib = 0; ib < n; ib++)
                if (table[ia][ib] < minUtility) table[ia][ib] = 0;
        return new CuptTable(table, itemToIdx);
    }

    // =========================================================================
    // Depth 0 — 1-sequences + SUR
    // =========================================================================

    private void mineFirstTime(List<SequenceData> database,
                               List<Integer> sequenceUtils) throws IOException {
        // Per item: utility, child combined PEU, child S-PEU (per-seq max, summed).
        Map<Integer, Integer> mapItemUtility  = new HashMap<>();
        Map<Integer, Integer> mapItemBound = new HashMap<>();  // no zero clause: width
        Map<Integer, Integer> mapItemGate  = new HashMap<>();  // Definition 12: gates
        Map<Integer, Integer> mapItemSPEU     = new HashMap<>();
        Map<Integer, UtilityChain> mapItemChains = new HashMap<>();

        for (SequenceData seq : database) {
            for (int r = 0; r < seq.itemNames.length; r++) {
                int item = seq.itemNames[r];
                int maxU = 0, maxBound = 0, maxGate = 0, maxSPEU = 0;
                UtilityChainEntry entry = null;

                for (int c = 0; c < seq.matrixItemUtility[r].length; c++) {
                    int u = seq.matrixItemUtility[r][c];
                    if (u > 0) {
                        int rest = seq.matrixItemRemainingUtility[r][c];
                        if (entry == null) {
                            entry = entryPool.isEmpty() ? new UtilityChainEntry(seq, r) : entryPool.poll();
                            entry.reset(seq, r);
                        }
                        entry.addElement(c, u, rest);
                        countElement();
                        if (u > maxU) maxU = u;
                        // Same split as CandidateInfo: `bound` (no zero clause)
                        // covers the 1-sequence itself and is what the width
                        // filter may use; `gate` applies Definition 12 and is
                        // valid for descendants only.
                        int bound = u + rest;
                        int gate  = peu(u, rest);
                        int speu  = peu(u, seq.matrixInterRemaining[c]);
                        if (bound > maxBound) maxBound = bound;
                        if (gate > maxGate)   maxGate  = gate;
                        if (speu > maxSPEU)   maxSPEU  = speu;
                    }
                }
                if (entry != null) {
                    mapItemUtility.merge(item, maxU, Integer::sum);
                    mapItemBound.merge(item, maxBound, Integer::sum);
                    mapItemGate.merge(item, maxGate, Integer::sum);
                    mapItemSPEU.merge(item, maxSPEU, Integer::sum);
                    mapItemChains.computeIfAbsent(item, x -> new UtilityChain()).addEntry(entry);
                    chainEntries++;
                }
            }
        }

        // SUR over the set of 1-, 2- and q-sequences (Definition 11).
        this.minUtility = Math.max(surFloor,
                SurThreshold.compute(database, sequenceUtils, mapItemUtility, k));

        // Depth-0 width pruning uses the NO-zero-clause bound, because an item
        // dropped here is never emitted and the bound must therefore cover the
        // 1-sequence itself. (RSU degenerates to this when the prefix is empty.)
        boolean[] promisingItems = new boolean[maxItemId + 1];
        List<int[]> candidates = new ArrayList<>();  // {item, utility, gateMax, gateI, gateS}
        for (Entry<Integer, Integer> e : mapItemBound.entrySet()) {
            int item = e.getKey();
            candDepth0++;
            if (e.getValue() < minUtility) { prunedWidthS++; continue; }
            promisingItems[item] = true;
            int u     = mapItemUtility.getOrDefault(item, 0);
            int gateI = mapItemGate.getOrDefault(item, 0);
            int speu  = mapItemSPEU.getOrDefault(item, 0);
            int gateS = splitSGate ? speu : gateI;
            candidates.add(new int[]{item, u, Math.max(gateI, gateS), gateI, gateS});
        }

        CuptTable cupt = useCupt ? computeCUPT(database, promisingItems) : null;

        // Phase 1: emit 1-sequences utility-descending (raises minUtility fast).
        candidates.sort((a, b) -> b[1] - a[1]);
        for (int[] cand : candidates) {
            if (cand[1] >= minUtility) {
                patternBuffer[0] = cand[0];
                updateTKList(patternBuffer, 1, cand[1]);
            }
        }

        // Phase 2: gate-descending DFS with early break.
        candidates.sort((a, b) -> b[2] - a[2]);
        for (int idx = 0; idx < candidates.size(); idx++) {
            int[] cand = candidates.get(idx);
            if (cand[2] < minUtility) {
                prunedGateI += candidates.size() - idx;
                prunedGateS += candidates.size() - idx;
                break;
            }
            boolean canI = cand[3] >= minUtility;
            boolean canS = cand[4] >= minUtility;
            if (!canI) prunedGateI++;
            if (!canS) prunedGateS++;

            if ((canI || canS) && (maxPatternLength < 0 || 1 < maxPatternLength)) {
                patternBuffer[0] = cand[0];
                UtilityChain chain = mapItemChains.get(cand[0]);
                if (chain != null && !chain.entries.isEmpty()) {
                    project(patternBuffer, 1, chain, 1, cupt, canI, canS);
                    returnChainToPool(chain);
                }
            }
        }
        MemoryLogger.getInstance().checkMemory();
    }

    // =========================================================================
    // Recursive projection — shared substrate, flag-selected bounds
    // =========================================================================

    private static class CandidateInfo {
        final int item;
        final boolean iExtension;
        int utility;    // per-seq max newU, summed — the pattern's own utility
        // Two PEU-family accumulators, and they must not be conflated:
        //   childBound = per-seq max (newU + rest), NO zero clause. Bounds the
        //     candidate ITSELF as well as its descendants, so it is the only
        //     one admissible as a WIDTH filter (a candidate dropped there is
        //     never emitted).
        //   childGate  = same, but occurrences with rest == 0 contribute 0
        //     (published Definition 12). Sound for DESCENDANTS only — such an
        //     occurrence admits no extension — so it may gate recursion but
        //     must never decide emission.
        int childBound;
        int childGate;
        int speu;       // per-seq max peu(newU, inter), summed — S-gate when splitSGate
        int rsu;        // per-seq max peu(parentU, parentRest), summed — width when !tightWidth
        UtilityChain chain = null;
        int lastSeenId = -1;
        int lastSeenMaxU, lastSeenMaxBound, lastSeenMaxGate, lastSeenMaxSPEU, lastSeenMaxRSU;

        CandidateInfo(int item, boolean iExt) { this.item = item; this.iExtension = iExt; }

        void see(int seqId, int newU, int bound, int gate, int speu, int rsuContrib) {
            if (lastSeenId != seqId) {
                flush();
                lastSeenId       = seqId;
                lastSeenMaxU     = newU;
                lastSeenMaxBound = bound;
                lastSeenMaxGate  = gate;
                lastSeenMaxSPEU  = speu;
                lastSeenMaxRSU   = rsuContrib;
            } else {
                if (newU > lastSeenMaxU)         lastSeenMaxU     = newU;
                if (bound > lastSeenMaxBound)    lastSeenMaxBound = bound;
                if (gate > lastSeenMaxGate)      lastSeenMaxGate  = gate;
                if (speu > lastSeenMaxSPEU)      lastSeenMaxSPEU  = speu;
                if (rsuContrib > lastSeenMaxRSU) lastSeenMaxRSU   = rsuContrib;
            }
        }
        void flush() {
            if (lastSeenId != -1) {
                utility    += lastSeenMaxU;
                childBound += lastSeenMaxBound;
                childGate  += lastSeenMaxGate;
                speu       += lastSeenMaxSPEU;
                rsu        += lastSeenMaxRSU;
            }
        }
    }

    /** Per-item 1-sequence utilities: sum over sequences of the best occurrence. */
    static Map<Integer, Integer> itemUtilities(List<SequenceData> database) {
        Map<Integer, Integer> iu = new HashMap<>();
        for (SequenceData seq : database) {
            for (int r = 0; r < seq.itemNames.length; r++) {
                int best = 0;
                int[] row = seq.matrixItemUtility[r];
                for (int c = 0; c < row.length; c++) if (row[c] > best) best = row[c];
                iu.merge(seq.itemNames[r], best, Integer::sum);
            }
        }
        return iu;
    }

    private int width(CandidateInfo ci)  { return tightWidth ? ci.childBound : ci.rsu; }
    private int gateI(CandidateInfo ci)  { return ci.childGate; }
    private int gateS(CandidateInfo ci)  { return splitSGate ? ci.speu : ci.childGate; }

    /** PEU with the published zero clause (Definition 12). */
    private static int peu(int utility, int remaining) {
        return remaining > 0 ? utility + remaining : 0;
    }

    private void project(int[] prefix, int prefixLength,
                         UtilityChain parentChain, int itemCount,
                         CuptTable cupt,
                         boolean parentCanI, boolean parentCanS) throws IOException {
        if (retightenPending) retighten();
        projections++;
        int lastItem = prefix[prefixLength - 1];
        // The prefix itself may have been retired since this call was queued.
        // Every descendant contains it, so the whole subtree is below threshold.
        if (retired != null && retired[lastItem]) return;
        // Pattern length is the natural depth index: the S-scan below extends a
        // pattern of itemCount items, so its work is attributed to that level.
        final int depth = Math.min(itemCount, DEPTH_BUCKETS - 1);

        // ---- I-extension scan (single pass: width + utility + gates) --------
        Map<Integer, CandidateInfo> iExtMap = new HashMap<>();
        if (parentCanI) {
            for (UtilityChainEntry entry : parentChain.entries) {
                SequenceData seq = entry.sequence;
                int rowLast = entry.anchorRow;
                for (UtilityChainElement elem : entry.elements) {
                    int col = elem.itemsetID;
                    int parentPEU = peu(elem.utility, elem.restUtility);
                    for (int r = rowLast + 1; r < seq.itemNames.length; r++) {
                        // Retired mid-run: appears in no pattern above the
                        // current threshold, so it generates no candidate.
                        if (retired != null && retired[seq.itemNames[r]]) continue;
                        int u = seq.matrixItemUtility[r][col];
                        if (u <= 0) continue;
                        int newU = elem.utility + u;
                        int rest = seq.matrixItemRemainingUtility[r][col];
                        CandidateInfo ci = iExtMap.computeIfAbsent(seq.itemNames[r],
                                x -> new CandidateInfo(x, true));
                        ci.see(seq.id, newU, newU + rest, peu(newU, rest),
                               peu(newU, seq.matrixInterRemaining[col]), parentPEU);
                        scanStepsI++;
                    }
                }
            }
        }
        List<CandidateInfo> iCandidates = new ArrayList<>();
        for (CandidateInfo ci : iExtMap.values()) {
            ci.flush();
            candI++;
            if (width(ci) >= minUtility) iCandidates.add(ci);
            else prunedWidthI++;
        }

        // ---- S-extension scan (all occurrences; CUPT pre-filter if on) ------
        Map<Integer, CandidateInfo> sExtMap = new HashMap<>();
        if (parentCanS && mergedChains) {
            // Column-merged scan: two-pointer over (parent elements, occurrence
            // columns). newU at column ca uses the running max of parent element
            // values before ca — the same Bellman maximum the pairwise scan
            // reaches via per-sequence max over dominated pairs.
            for (UtilityChainEntry entry : parentChain.entries) {
                SequenceData seq = entry.sequence;
                int nbCols = seq.matrixItemUtility[0].length;
                List<UtilityChainElement> elems = entry.elements;
                int nbElems  = elems.size();
                int firstCol = elems.get(0).itemsetID;
                for (int r = 0; r < seq.itemNames.length; r++) {
                    int item = seq.itemNames[r];
                    if (retired != null && retired[item]) continue;
                    if (cupt != null && !cupt.allows(lastItem, item, minUtility)) {
                        cuptOccSkips++;
                        continue;
                    }
                    int pi = 0, runMaxVal = 0, runMaxPEU = 0;
                    CandidateInfo ci = null;
                    for (int ca = seq.nextPos[r][firstCol + 1]; ca < nbCols;
                             ca = seq.nextPos[r][ca + 1]) {
                        while (pi < nbElems && elems.get(pi).itemsetID < ca) {
                            UtilityChainElement e = elems.get(pi);
                            if (e.utility > runMaxVal) runMaxVal = e.utility;
                            int p = peu(e.utility, e.restUtility);
                            if (p > runMaxPEU) runMaxPEU = p;
                            pi++;
                        }
                        int u    = seq.matrixItemUtility[r][ca];
                        int newU = runMaxVal + u;
                        int rest = seq.matrixItemRemainingUtility[r][ca];
                        if (ci == null) ci = sExtMap.computeIfAbsent(item,
                                x -> new CandidateInfo(x, false));
                        ci.see(seq.id, newU, newU + rest, peu(newU, rest),
                               peu(newU, seq.matrixInterRemaining[ca]), runMaxPEU);
                        scanStepsS++;
                        if (depth < DEPTH_BUCKETS) {
                            scanStepsSByDepth[depth]++;
                            dominatedPairsByDepth[depth] += pi;
                        }
                        // pi is now exactly |{parent elements before column ca}|.
                        // Summing it over the visited columns counts the pairs a
                        // pairwise scan would visit ON THIS PARENT CHAIN, by
                        // Fubini: sum_c |{e : e.col < c}| = sum_e |{c : c > e.col}|.
                        // Valid per step only: under m0 the chains below depth 1
                        // are larger, so the redundancy compounds and the global
                        // pairwise cost exceeds this sum whenever pbar > 1.
                        dominatedPairs += pi;
                    }
                }
            }
        } else if (parentCanS) {
            for (UtilityChainEntry entry : parentChain.entries) {
                SequenceData seq = entry.sequence;
                int nbCols = seq.matrixItemUtility[0].length;
                for (UtilityChainElement elem : entry.elements) {
                    int col = elem.itemsetID;
                    int parentPEU = peu(elem.utility, elem.restUtility);
                    for (int r = 0; r < seq.itemNames.length; r++) {
                        int item = seq.itemNames[r];
                        if (retired != null && retired[item]) continue;
                        if (cupt != null && !cupt.allows(lastItem, item, minUtility)) {
                            cuptOccSkips++;
                            continue;
                        }
                        for (int nextC = seq.nextPos[r][col + 1]; nextC < nbCols;
                                 nextC = seq.nextPos[r][nextC + 1]) {
                            int u    = seq.matrixItemUtility[r][nextC];
                            int newU = elem.utility + u;
                            int rest = seq.matrixItemRemainingUtility[r][nextC];
                            CandidateInfo ci = sExtMap.computeIfAbsent(item,
                                    x -> new CandidateInfo(x, false));
                            ci.see(seq.id, newU, newU + rest, peu(newU, rest),
                                   peu(newU, seq.matrixInterRemaining[nextC]), parentPEU);
                            scanStepsS++;
                            if (depth < DEPTH_BUCKETS) scanStepsSByDepth[depth]++;
                        }
                    }
                }
            }
        }
        List<CandidateInfo> sCandidates = new ArrayList<>();
        for (CandidateInfo ci : sExtMap.values()) {
            ci.flush();
            candS++;
            if (width(ci) >= minUtility) sCandidates.add(ci);
            else prunedWidthS++;
        }

        // ---- Phase 1: emit utility-descending (merge of two sorted halves) --
        iCandidates.sort((a, b) -> b.utility - a.utility);
        sCandidates.sort((a, b) -> b.utility - a.utility);

        List<CandidateInfo> seqList = new ArrayList<>(iCandidates.size() + sCandidates.size());
        int ii = 0, si = 0;
        int iSz = iCandidates.size(), sSz = sCandidates.size();
        while (ii < iSz || si < sSz) {
            CandidateInfo ci;
            if (si >= sSz || (ii < iSz && iCandidates.get(ii).utility >= sCandidates.get(si).utility)) {
                ci = iCandidates.get(ii++);
            } else {
                ci = sCandidates.get(si++);
            }
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
            // Deferred chain building: keep only candidates whose gates can
            // still beat the (possibly raised) threshold.
            if (gateI(ci) >= minUtility || gateS(ci) >= minUtility) seqList.add(ci);
        }

        // ---- Chain build — entry-first, only for survivors ------------------
        for (CandidateInfo ci : seqList) {
            ci.chain = chainPool.isEmpty() ? new UtilityChain() : chainPool.poll();
            if (ci.iExtension) dispatchI[ci.item] = ci;
            else               dispatchS[ci.item] = ci;
        }

        for (UtilityChainEntry entry : parentChain.entries) {
            SequenceData seq = entry.sequence;
            int nbCols = seq.matrixItemUtility[0].length;
            int pendingCount = 0;

            for (int r = 0; r < seq.itemNames.length; r++) {
                int itemR = seq.itemNames[r];
                CandidateInfo ciI = dispatchI[itemR];
                CandidateInfo ciS = dispatchS[itemR];
                if (ciI == null && ciS == null) continue;

                if (ciI != null && r > entry.anchorRow) {
                    for (UtilityChainElement elem : entry.elements) {
                        int u = seq.matrixItemUtility[r][elem.itemsetID];
                        if (u <= 0) continue;
                        UtilityChainEntry pe = pendingEntryByItem[itemR];
                        if (pe == null) {
                            pe = entryPool.isEmpty() ? new UtilityChainEntry(seq, r) : entryPool.poll();
                            pe.reset(seq, r);
                            pendingEntryByItem[itemR] = pe;
                            pendingEntryDirty[pendingCount++] = itemR;
                        }
                        pe.addElement(elem.itemsetID, elem.utility + u,
                                      seq.matrixItemRemainingUtility[r][elem.itemsetID]);
                        countElement();
                    }
                    if (pendingEntryByItem[itemR] != null) {
                        ciI.chain.addEntry(pendingEntryByItem[itemR]);
                        chainEntries++;
                        pendingEntryByItem[itemR] = null;
                        pendingCount--;
                    }
                }

                if (ciS != null && mergedChains) {
                    // Column-merged build: one element per occurrence column,
                    // carrying the Bellman max val; two-pointer, ascending
                    // columns (invariant relied on by the merged scan above).
                    List<UtilityChainElement> elems = entry.elements;
                    int nbElems  = elems.size();
                    int firstCol = elems.get(0).itemsetID;
                    int pi = 0, runMaxVal = 0;
                    for (int ca = seq.nextPos[r][firstCol + 1]; ca < nbCols;
                             ca = seq.nextPos[r][ca + 1]) {
                        while (pi < nbElems && elems.get(pi).itemsetID < ca) {
                            int v = elems.get(pi).utility;
                            if (v > runMaxVal) runMaxVal = v;
                            pi++;
                        }
                        int u    = seq.matrixItemUtility[r][ca];
                        int rest = seq.matrixItemRemainingUtility[r][ca];
                        UtilityChainEntry pe = pendingEntryByItem[itemR];
                        if (pe == null) {
                            pe = entryPool.isEmpty() ? new UtilityChainEntry(seq, r) : entryPool.poll();
                            pe.reset(seq, r);
                            pendingEntryByItem[itemR] = pe;
                            pendingEntryDirty[pendingCount++] = itemR;
                        }
                        pe.addElement(ca, runMaxVal + u, rest);
                        countElement();
                    }
                } else if (ciS != null) {
                    // All occurrences after each element's column (soundness fix).
                    for (UtilityChainElement elem : entry.elements) {
                        for (int nextC = seq.nextPos[r][elem.itemsetID + 1]; nextC < nbCols;
                                 nextC = seq.nextPos[r][nextC + 1]) {
                            int u    = seq.matrixItemUtility[r][nextC];
                            int newU = elem.utility + u;
                            int rest = seq.matrixItemRemainingUtility[r][nextC];
                            UtilityChainEntry pe = pendingEntryByItem[itemR];
                            if (pe == null) {
                                pe = entryPool.isEmpty() ? new UtilityChainEntry(seq, r) : entryPool.poll();
                                pe.reset(seq, r);
                                pendingEntryByItem[itemR] = pe;
                                pendingEntryDirty[pendingCount++] = itemR;
                            }
                            pe.addElement(nextC, newU, rest);
                            countElement();
                        }
                    }
                }
            }
            for (int i = 0; i < pendingCount; i++) {
                int itemR = pendingEntryDirty[i];
                UtilityChainEntry pe = pendingEntryByItem[itemR];
                pendingEntryByItem[itemR] = null;
                dispatchS[itemR].chain.addEntry(pe);
                chainEntries++;
            }
        }
        for (CandidateInfo ci : seqList) {
            if (ci.iExtension) dispatchI[ci.item] = null;
            else               dispatchS[ci.item] = null;
        }

        // ---- Phase 2: gate-descending DFS with early break ------------------
        seqList.sort((a, b) -> Math.max(gateI(b), gateS(b)) - Math.max(gateI(a), gateS(a)));

        for (int idx = 0; idx < seqList.size(); idx++) {
            CandidateInfo ci = seqList.get(idx);
            boolean canI = gateI(ci) >= minUtility;
            boolean canS = gateS(ci) >= minUtility;

            if (!canI && !canS) {
                for (int j = idx; j < seqList.size(); j++) {
                    returnChainToPool(seqList.get(j).chain);
                    seqList.get(j).chain = null;
                }
                prunedGateI += seqList.size() - idx;
                prunedGateS += seqList.size() - idx;
                break;
            }
            if (!canI) prunedGateI++;
            if (!canS) prunedGateS++;

            if (ci.chain.entries.isEmpty()) {
                returnChainToPool(ci.chain);
                ci.chain = null;
                continue;
            }

            if (ci.iExtension) {
                prefix[prefixLength] = ci.item;
                project(prefix, prefixLength + 1, ci.chain, itemCount, cupt, canI, canS);
            } else {
                if (maxPatternLength < 0 || itemCount + 1 <= maxPatternLength) {
                    prefix[prefixLength]     = -1;
                    prefix[prefixLength + 1] = ci.item;
                    project(prefix, prefixLength + 2, ci.chain, itemCount + 1, cupt, canI, canS);
                } else {
                    returnChainToPool(ci.chain);
                    ci.chain = null;
                    continue;
                }
            }
            returnChainToPool(ci.chain);
            ci.chain = null;
        }
        MemoryLogger.getInstance().checkMemory();
    }

    // =========================================================================
    // TKList — identical rule to AlgoTKUS
    // =========================================================================

    private void updateTKList(int[] prefix, int prefixLength, int utility) {
        int[] pattern = Arrays.copyOf(prefix, prefixLength);
        tkList.computeIfAbsent(utility, u -> new ArrayList<>()).add(pattern);
        tkListSize++;

        while (!tkList.isEmpty()) {
            int minKey = tkList.firstKey();
            List<int[]> minBucket = tkList.get(minKey);
            int countAboveMin = tkListSize - minBucket.size();
            if (countAboveMin >= k) {
                tkListSize -= minBucket.size();
                tkList.remove(minKey);
            } else break;
        }
        if (tkListSize >= k && !tkList.isEmpty()) minUtility = tkList.firstKey();

        // Iterative vocabulary tightening: the threshold has outgrown the one
        // the vocabulary was last filtered at, so a smaller least fixpoint
        // exists. Only flagged here; the recomputation happens at the next
        // projection boundary, where the state is simple.
        if (fixpointItems && iterateFixpoint && !retightenPending
                && minUtility >= nextCheckTheta) {
            retightenPending = true;
        }
    }

    private void writeTopKToFile() throws IOException {
        for (Map.Entry<Integer, List<int[]>> e : tkList.descendingMap().entrySet())
            for (int[] pattern : e.getValue())
                outputResult.writePattern(pattern, pattern.length, e.getKey());
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    public String countersString() {
        return "cands0=" + candDepth0 + ";candsI=" + candI + ";candsS=" + candS
             + ";prunedWidthI=" + prunedWidthI + ";prunedWidthS=" + prunedWidthS
             + ";prunedGateI=" + prunedGateI + ";prunedGateS=" + prunedGateS
             + ";cuptOccSkips=" + cuptOccSkips + ";projections=" + projections
             + ";vocabBefore=" + vocabBefore + ";vocabAfter=" + vocabAfter
             + ";fixpointRounds=" + fixpointRounds
             + ";chainElements=" + chainElements + ";chainEntries=" + chainEntries
             + ";peakChainElements=" + peakChainElements
             + ";scanStepsI=" + scanStepsI + ";scanStepsS=" + scanStepsS
             + ";dominatedPairs=" + dominatedPairs
             + ";surTheta=" + surTheta + ";retightenings=" + retightenings
             + ";itemsRetired=" + itemsRetired
             + ";scanStepsSByDepth=" + compact(scanStepsSByDepth)
             + ";dominatedPairsByDepth=" + compact(dominatedPairsByDepth);
    }

    /** "a|b|c" up to the last non-zero bucket; "" when nothing was recorded. */
    private static String compact(long[] v) {
        int last = -1;
        for (int i = 0; i < v.length; i++) if (v[i] != 0) last = i;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= last; i++) {
            if (i > 0) sb.append('|');
            sb.append(v[i]);
        }
        return sb.toString();
    }

    public void printStats() {
        System.out.println("========= AlgoTKCM[" + configName() + "] STATISTICS =========");
        System.out.println(" Total time     ~ " + timer.formatElapsed(Timer.TimeUnit.MILLISECONDS));
        System.out.println(" Memory (peak)  ~ " + MemoryLogger.formatMemory(memoryUsed, MemoryLogger.MemoryUnit.MB));
        System.out.println(" Patterns found : " + patternCount);
        System.out.println(" minUtility     : " + minUtility);
        System.out.println(" " + countersString().replace(";", "\n "));
        System.out.println("======================================================");
    }

    public static void main(String[] args) throws IOException {
        String input  = args.length > 0 ? args[0] : "../datasets/uspan.txt";
        String output = args.length > 1 ? args[1] : "../outputs/miner.txt";
        int k         = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        String cfg    = args.length > 3 ? args[3] : "w1s1c1m1f1";
        // Every flag is threaded, including r and a. Dropping one here makes the
        // printed config name disagree with what actually ran.
        AlgoTKCM m = new AlgoTKCM(cfg.contains("w1"), cfg.contains("s1"),
                                    cfg.contains("c1"), cfg.contains("m1"),
                                    cfg.contains("f1"), cfg.contains("r1"));
        m.runAlgorithm(input, output, k);
        m.printStats();
    }
}
