import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Dataset screening for top-k HUSPM, in two stages.
 *
 * <p>The point is to decide whether a candidate dataset is worth adding to the
 * evaluation — and which contribution it can evidence — WITHOUT implementing or
 * running the slow configuration. Every number printed here is either a
 * property of the data or comes from one run of the fast configuration.</p>
 *
 * <p><b>Stage 1</b> costs seconds and never mines: dataset shape, the SUR
 * threshold, that threshold as a share of total utility, and how far the
 * least-fixpoint filter reduces the vocabulary at it. This alone answers "is
 * this cell even on scale" and "does the vocabulary lever exist here".</p>
 *
 * <p><b>Stage 2</b> runs the shipped configuration once under a watchdog and
 * reads the counters. It yields the redundancy factor and the S-scan share,
 * which together decide whether the merged representation can pay. If it
 * overruns, stage 1 still stands and the verdict says which structural cause
 * applies.</p>
 *
 * <p>Thresholds in the verdict are an empirical lookup calibrated on the four
 * datasets measured on the official machine, not a formula. Where a candidate
 * falls between calibrated bands the verdict says so rather than guessing.</p>
 *
 * Usage: java ScreenDataset &lt;dataset&gt; &lt;k&gt; [timeoutSeconds]
 */
public class ScreenDataset {

    /** 1 = loading/SUR/fixpoint, 2 = the mining run, 3 = finished. */
    private static final int[] stage = {0};
    private static final int[] kk = {0};
    private static final double[] avg = {Double.NaN}, rem = {Double.NaN};
    private static final long[] wide = {0};

    /**
     * A screen measures a (dataset, k) PAIR, so every verdict must name its k.
     * The first version phrased them as though they were about the dataset, and
     * two results showed why that is wrong: Kosarak10k does not finish at k=100
     * and was reported as an unmatched failure mode, although it completes in
     * 0.69 s at k=16 — the same cliff, seen from the far side; and SIGN was
     * reported as having NO LEVER for the filter at 28.8 % removed at k=100,
     * while at k=20 it removes 34.5 % and the filter measurably helps.
     *
     * So a run that overruns is first re-screened at a tenth of k. Only if that
     * also overruns is a structural cause offered.
     */
    /**
     * The three failure causes, in the order a single measurement can actually
     * attribute them.
     *
     * <p>Width is tested first because it is the only one that survives the
     * other two clearing. OnlineRetail_II_all has short sequences (5.4
     * itemsets) and a strong vocabulary lever (81.9 % removed), so both other
     * criteria pass it, and it still does not finish; its widest itemset holds
     * 257 items, which is 2^257 sub-itemsets of I-extension candidates.</p>
     *
     * <p>257 is the count the LOADER sees. The raw file has 275 item tokens in
     * that itemset and the loader merges the duplicates, so quoting the file
     * figure here would put two numbers on one quantity. The tool must be
     * calibrated against what the algorithm actually walks.</p>
     *
     * <p>Each threshold is an empirical lookup calibrated on a measured
     * example, named in the message so the number can be checked.</p>
     */
    static void cause(double avgLen, double removed, long widestCol) {
        if (widestCol >= 32)
            System.out.printf("     Cause looks like WIDE ITEMSETS (widest holds %,d items;%n"
                    + "     OnlineRetail_II_all is the measured example at 257). I-extension%n"
                    + "     candidate generation is exponential in the width of a single%n"
                    + "     itemset, and no contribution here addresses it.%n", widestCol);
        else if (removed < 50)
            System.out.printf("     Cause looks like an IRREDUCIBLE VOCABULARY (%.1f%% removed;%n"
                    + "     bms at k=100 is the measured example at 29.2%%, and the same dataset%n"
                    + "     removes 54.3%% at k=10 — so quote this WITH its k). The vocabulary%n"
                    + "     lever is absent here, so this dataset cannot evidence the filter.%n", removed);
        else if (avgLen > 30)
            System.out.printf("     Cause looks like LONG SEQUENCES (%.1f itemsets; fifa is the%n"
                    + "     measured example at 36.2, MicroblogPCU at 74.4). Depth drives the%n"
                    + "     cost and no contribution here addresses depth.%n", avgLen);
        else
            System.out.println("     Cause not matched to a measured pattern — worth investigating,\n"
                    + "     since it would be a FOURTH failure mode.");
    }

    static void verdictTimeout(int k, double avgLen, double removed, long widestCol) {
        System.out.println("\n-- verdict --");
        System.out.printf("   FEASIBILITY at k=%d: the shipped configuration did not finish.%n", k);
        if (k > 10) {
            System.out.printf("     Before reading a structural cause into this, re-screen at a%n"
                    + "     smaller k — the same dataset can sit either side of its own cliff:%n"
                    + "        bash screen.sh <dataset> %d%n", Math.max(1, k / 10));
        }
        cause(avgLen, removed, widestCol);
        System.out.println("   ROLE at this k: infeasibility row only. Report the stage-1\n"
                + "     threshold so the cell is shown to be on scale (rule 2.2.7).");
    }

    public static void main(String[] args) throws Exception {
        String ds = args[0];
        int k = Integer.parseInt(args[1]);
        int limit = args.length > 2 ? Integer.parseInt(args[2]) : 600;
        String name = ds.substring(ds.lastIndexOf('/') + 1);

        System.out.println("=== screening " + name + " at k=" + k
                + " (limit " + limit + " s) ===");

        // The watchdog covers BOTH stages. Stage 1 is advertised as costing
        // seconds and usually does, but SUR enumerates item pairs, so on a
        // dataset with a very large vocabulary it is not cheap at all —
        // MicroblogPCU has 50,505 distinct items and had not finished stage 1
        // after 35 s when this guard covered stage 2 only. A screen that can
        // hang is not a screen.
        stage[0] = 1; kk[0] = k;
        Thread guard = new Thread(() -> {
            try {
                Thread.sleep(limit * 1000L);
                if (stage[0] != 3) {
                    System.out.printf("%n-- DID NOT FINISH within %d s, reached stage %d --%n",
                            limit, stage[0]);
                    if (stage[0] == 1)
                        System.out.println("   Stage 1 itself overran. SUR enumerates item pairs, so a\n"
                                + "   very large vocabulary makes even the cheap stage expensive.\n"
                                + "   That is itself a finding about the dataset: report the item\n"
                                + "   count and screen it at a larger k, where SUR has less to rank.");
                    else
                        verdictTimeout(kk[0], avg[0], rem[0], wide[0]);
                    Runtime.getRuntime().halt(124);
                }
            } catch (InterruptedException ignored) { }
        });
        guard.setDaemon(true);
        guard.start();

        // ---- stage 1: properties of the data; no mining -------------------
        long t0 = System.nanoTime();
        Dataset.DatasetResult res = new Dataset(false, 2000).loadDataset(ds, 0);
        double loadSec = (System.nanoTime() - t0) / 1e9;
        List<SequenceData> db = res.sequenceDatabase;

        long itemsets = 0, totalUtil = 0, pairs = 0, occ = 0, oneItemCols = 0, cols = 0;
        long itemsInCols = 0, widestCol = 0;
        for (SequenceData s : db) {
            int nbCols = s.matrixItemUtility[0].length;
            // Count only NON-EMPTY columns. Every published file here carries a
            // double space before SUtility, which the loader splits into a
            // trailing empty itemset — one per sequence, holding no item and no
            // utility. It is inert for mining (no pattern can occur in it) but
            // it inflated the average length reported here by exactly 1 on
            // every dataset except leviathan, which we generated with a single
            // space and which therefore has none.
            for (int c = 0; c < nbCols; c++) {
                int inCol = 0;
                for (int[] row : s.matrixItemUtility) if (row[c] > 0) inCol++;
                if (inCol == 0) continue;
                cols++; itemsets++;
                if (inCol == 1) oneItemCols++;
                itemsInCols += inCol;
                if (inCol > widestCol) widestCol = inCol;
            }
            for (int[] row : s.matrixItemUtility) {
                int n = 0;
                for (int u : row) { if (u > 0) n++; totalUtil += u; }
                if (n > 0) { pairs++; occ += n; }
            }
        }
        double avgLen = itemsets / (double) db.size();
        // Mean occurrences of an item within a sequence that contains it.
        //
        // This SETTLES the representation question without mining, in one
        // direction: if no item repeats within a sequence then no pattern can
        // occur twice there, so every chain entry holds exactly one element,
        // so p-bar = 1 and merging is provably inert. Confirmed exactly on the
        // measured datasets — SIGN and Kosarak10k both read 1.000 here and
        // 1.000 for p-bar, while BIBLE reads 1.213 / 1.851 and Yoochoose
        // 1.068 / 1.46.
        //
        // The other direction is only a signal: repetition above 1 makes
        // p-bar above 1 possible and the measured pairs amplify it (1.213 to
        // 1.851), but the magnitude is not predictable from here, because
        // p-bar is measured over patterns deep in the tree rather than over
        // single items. Stage 2 still has to run.
        double repeat = occ / (double) pairs;
        double singleItemPct = 100.0 * oneItemCols / cols;

        Map<Integer, Integer> iu = AlgoTKCM.itemUtilities(db);
        int theta = SurThreshold.compute(db, res.sequenceUtilities, iu, k);
        ItemFixpoint.Result fp = ItemFixpoint.compute(db, theta);
        double removed = 100.0 * (1 - fp.items.size() / (double) fp.initialItems);

        System.out.printf("%n-- stage 1 (data properties, %.2f s) --%n", loadSec);
        System.out.printf("   sequences        %,12d%n", db.size());
        System.out.printf("   distinct items   %,12d%n", fp.initialItems);
        System.out.printf("   avg length       %12.1f  itemsets per sequence%n", avgLen);
        System.out.printf("   SUR threshold    %,12d  = %.3f%% of total utility%n",
                theta, 100.0 * theta / Math.max(1, totalUtil));
        System.out.printf("   fixpoint         %,12d -> %,d  (%.1f%% removed, %d rounds)%n",
                fp.initialItems, fp.items.size(), removed, fp.rounds);
        System.out.printf("   repetition       %12.3f  occurrences per (sequence, item)%n", repeat);
        System.out.printf("   single-item      %11.1f%%  of itemsets%n", singleItemPct);
        System.out.printf("   itemset width    %12.2f  items per itemset, widest %,d%n",
                itemsInCols / (double) Math.max(1, cols), widestCol);
        if (widestCol <= 1) {
            System.out.println("\n   >> Every itemset holds one item, so the search generates NO");
            System.out.println("      I-extensions at all — the tree is pure S-extension. Six of the");
            System.out.println("      ten catalogued datasets read exactly 1 here, which is why an");
            System.out.println("      S-share of 100% is a property of the DATA, not a finding.");
        }
        if (repeat <= 1.0005) {
            System.out.println("\n   >> No item repeats within a sequence, so no pattern occurs twice");
            System.out.println("      in one. Every chain entry holds one element, p-bar = 1, and the");
            System.out.println("      merged representation is PROVABLY INERT here — settled without");
            System.out.println("      mining. (SIGN and Kosarak10k both read 1.000 and measure 1.000.)");
        }

        // ---- stage 2: one run of the shipped configuration ----------------
        final AlgoTKCM eng = new AlgoTKCM(true, true, true, true, true);
        File out = File.createTempFile("screen", ".txt");
        out.deleteOnExit();
        stage[0] = 2; avg[0] = avgLen; rem[0] = removed; wide[0] = widestCol;

        long t1 = System.nanoTime();
        eng.runAlgorithm(ds, out.getPath(), k);
        double mineSec = (System.nanoTime() - t1) / 1e9;
        stage[0] = 3;
        guard.interrupt();
        out.delete();

        double pbar = eng.scanStepsS > 0 ? eng.dominatedPairs / (double) eng.scanStepsS : Double.NaN;
        double sShare = (eng.scanStepsS + eng.scanStepsI) > 0
                ? 100.0 * eng.scanStepsS / (eng.scanStepsS + eng.scanStepsI) : 0.0;
        double loadShare = 100.0 * loadSec / mineSec;

        System.out.printf("%n-- stage 2 (one run of w1s1c1m1f1, %.2f s) --%n", mineSec);
        System.out.printf("   projections      %,12d%n", eng.projections);
        System.out.printf("   S-scan steps     %,12d%n", eng.scanStepsS);
        System.out.printf("   I-scan steps     %,12d%n", eng.scanStepsI);
        System.out.printf("   p-bar            %12s   (dominatedPairs / S-scan steps)%n",
                eng.scanStepsS > 0 ? String.format("%.3f", pbar) : "n/a (no S-scan)");
        System.out.printf("   S-scan share     %11.1f%%%n", sShare);
        // loadShare is deliberately NOT printed: it is machine-dependent (44 %
        // on the official machine against 25 % here for the same cell) and no
        // verdict may rest on it. Everything above this line is invariant.

        verdict(name, k, avgLen, removed, widestCol, pbar, sShare, loadShare, mineSec);
    }

    /**
     * Empirical lookup, calibrated on the four datasets measured on the
     * official machine. Bands a candidate does not fall into are reported as
     * unmeasured rather than interpolated.
     *
     *   merging   BIBLE     p-bar 1.85, S-share 100%  -> 8.9x and 21.8x
     *             Yoochoose p-bar 1.31, S-share 5.6%  -> 1.10x
     *             Kosarak   p-bar 1.00                -> 0.92-1.00x
     *             SIGN      p-bar 1.00                -> 0.93-0.94x
     * The fixpoint gets no one-run verdict: see the comment at its branch.
     * Vocabulary reduction is necessary but not sufficient, and the sufficient
     * quantity (the projections ratio) needs both configurations.
     */
    static void verdict(String name, int k, double avgLen, double removed, long widestCol,
                        double pbar, double sShare, double loadShare, double mineSec) {
        System.out.printf("%n-- verdict (at k=%d) --%n", k);

        if (Double.isNaN(pbar)) {
            System.out.println("   FEASIBILITY: the shipped configuration did not finish.");
            cause(avgLen, removed, widestCol);
            System.out.println("   ROLE: infeasibility row only. Report the stage-1 threshold so the"
                    + "\n     cell is shown to be on scale (rule 2.2.7).");
            return;
        }

        System.out.print("   MERGED CHAINS: ");
        if (Double.isNaN(pbar) || pbar <= 1.05)
            System.out.println("INERT. p-bar ~ 1, so both representations build the same\n"
                    + "     structure. Measured elsewhere at 0.92-1.00x, i.e. a small overhead.\n"
                    + "     Useful as a NEGATIVE case: it shows the criterion refusing.");
        else if (sShare >= 80)
            System.out.printf("PAYS. p-bar %.2f with %.0f%% of the work in the S-scan —%n"
                    + "     the band BIBLE sits in, where the measured effect is 8.9x to 21.8x.%n"
                    + "     THIS IS THE PROFILE THE PAPER MOST NEEDS A SECOND EXAMPLE OF.%n", pbar, sShare);
        else if (sShare < 20)
            System.out.printf("SMALL. p-bar %.2f but only %.1f%% of the work is in the%n"
                    + "     S-scan, so the ceiling is low whatever the redundancy — the band%n"
                    + "     Yoochoose sits in, measured at 1.10x.%n", pbar, sShare);
        else
            System.out.printf("UNMEASURED BAND. p-bar %.2f, S-share %.1f%% falls between the%n"
                    + "     calibrated cases (Yoochoose 5.6%%, BIBLE 100%%). Worth measuring%n"
                    + "     precisely BECAUSE it is uncalibrated.%n", pbar, sShare);

        // The fixpoint deliberately gets NO one-run verdict.
        //
        // The obvious proxy — how much vocabulary the filter removes — is
        // NECESSARY BUT NOT SUFFICIENT, and validating this screen against
        // already-measured datasets is what exposed that. Yoochoose k=1000 has
        // 92.2 % of its vocabulary removed, which the first version of this
        // code reported as "PAYS"; the measured outcome is a 40 % LOSS. The
        // reason is visible in the projections: 2,343 -> 2,263, a ratio of
        // 1.04, because the items removed were ones the bounds had already
        // pruned. Contrast Kosarak k=16 at 8,193,993 -> 57.
        //
        //   dataset          vocab removed   projections ratio   time ratio
        //   Yoochoose k=1000     92.2 %            1.04x           0.60x
        //   sign k=20            34.5 %            1.13x           1.09x
        //   BIBLE k=500          94.5 %            1.33x           1.44x
        //   Kosarak k=16         99.6 %       143,754x           962x
        //
        // The predictor that does track the outcome is the projections ratio —
        // and that needs BOTH configurations, so it is not a one-run quantity.
        // A second proxy I tried, loading as a share of the run, is worse than
        // useless: it is machine-dependent, and it read 44 % on the official
        // machine against 25 % here for the same cell.
        //
        // So this screen reports what it can measure and refuses the verdict it
        // cannot support.
        System.out.print("   FIXPOINT: ");
        if (removed < 30)
            System.out.printf("NO LEVER. Only %.1f%% of the vocabulary is removable — below%n"
                    + "     bms at k=100 (29.2%%), where nothing completes. The filter has%n"
                    + "     nothing to work%n"
                    + "     with, and this is the one direction the vocabulary figure settles.%n", removed);
        else
            System.out.printf("UNDECIDED from one run. %.1f%% of the vocabulary is removable,%n"
                    + "     which is necessary but NOT sufficient: Yoochoose removes 92.2%% and%n"
                    + "     still LOSES 40%%, because the items it drops were already pruned by%n"
                    + "     the bounds (projections 2,343 -> 2,263). The quantity that tracks the%n"
                    + "     outcome is the projections ratio, and that needs an f0/f1 A-B — two%n"
                    + "     runs, not one. Run it if this dataset is otherwise a candidate.%n", removed);

        System.out.println("   ROLE: " + (mineSec < 2
                ? "runs in under 2 s — usable, but too fast to separate configurations\n"
                  + "     reliably; prefer a larger k on this dataset."
                : "timings here are separable; usable for the comparison tables."));
    }
}
