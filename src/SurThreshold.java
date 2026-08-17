import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * SUR (Sequence Utility Raising) initial threshold.
 *
 * <p>Zhang, Du, Gan, Yu, "TKUS: Mining top-k high utility sequential
 * patterns", Information Sciences 570 (2021) 342--359, Definition 11 and
 * Algorithm 1 lines 2--4: {@code minutil} is initialised to the k-th highest
 * utility among <em>all 1-, 2-, and q-sequences</em> of the database.</p>
 *
 * <p>The safety argument is: if <em>k distinct patterns</em> are known to reach
 * utility u, then the k-th best pattern has utility at least u, so raising
 * {@code minutil} to u cannot discard a top-k pattern (their Proof 1).</p>
 *
 * <p><b>Distinctness is load-bearing and the three families overlap.</b> A
 * q-sequence of length two <em>is</em> a 2-sequence, and a q-sequence of
 * length one is a 1-sequence; feeding both copies to the ranking counts one
 * pattern twice, so the k-th largest can exceed the true k-th best utility and
 * patterns are lost. Minimal witness, at k=2:</p>
 *
 * <pre>
 *   1[1] -1 1[100] -1 -2 SUtility:101
 *   2[5] 3[5]      -1 -2 SUtility:10
 * </pre>
 *
 * <p>Here 101 arrives twice — once as the q-sequence utility, once as the
 * exact utility of the 2-sequence {@code <(1)(1)>} — although there is only
 * one such pattern. Undeduplicated, the 2nd largest is 101 and the genuine
 * runner-up {@code <(1)>}:100 is pruned away. This implementation therefore
 * keys every value by pattern identity before ranking, which is what the
 * published proof requires. The regression is kept in
 * {@code test/data/cupt_selfpair_counterexample.txt}.</p>
 *
 * <p>Shared by {@link AlgoTKUS} and {@link AlgoTKCM} so that the baseline and
 * the engine cannot drift apart on this strategy. The cost of the 2-sequence
 * pass is real work and is charged to the caller's measured runtime.</p>
 *
 * <h3>2-sequence utilities</h3>
 * A 2-sequence is either {@code <(a b)>} (both items in one itemset, an
 * I-extension) or {@code <(a)(b)>} (two itemsets, an S-extension). For a
 * pattern t and a sequence s, u(t, s) is the maximum over the occurrences of
 * t in s; u(t) sums that over the sequences containing t.
 */
final class SurThreshold {

    private SurThreshold() {}

    /**
     * The threshold as the corrected proof requires, ranking distinct patterns.
     *
     * @param database        the loaded sequence database
     * @param sequenceUtils   raw q-sequence utilities from the loader
     * @param itemUtilities   1-sequence utilities, keyed by item
     * @param k               desired number of patterns
     * @return the k-th highest utility over the three families, or 0 when
     *         fewer than k values exist
     */
    static int compute(List<SequenceData> database,
                       List<Integer> sequenceUtils,
                       Map<Integer, Integer> itemUtilities,
                       int k) {
        // A min-heap of size k yields the k-th largest in O(N log k). Values
        // enter once per DISTINCT pattern (see the class comment).
        PriorityQueue<Integer> topK = new PriorityQueue<>(k + 1);

        // 1-sequences: itemUtilities is already keyed by item, hence distinct.
        for (int u : itemUtilities.values()) offer(topK, u, k);

        // 2-sequences: keyed by (item, item, extension type), hence distinct.
        // Values are ranked straight out of the primitive map; materialising a
        // Map<Long,Integer> first would box every distinct pair, and there are
        // millions of them on the denser datasets.
        twoSequenceUtilities(database).offerValuesInto(topK, k);

        // q-sequences: a sequence of one or two items duplicates a pattern
        // already ranked above, with a value that cannot exceed the exact
        // utility computed there, so only sequences of three or more items
        // contribute. Sequences sharing a pattern are summed, which is both
        // deduplicated and tighter than taking one of them.
        for (int u : longSequenceUtilities(database, sequenceUtils, true).values()) offer(topK, u, k);

        return (topK.size() >= k) ? Math.max(0, topK.peek()) : 0;
    }

    /**
     * Deduplicated but not summed: the third point needed to separate two
     * changes that {@link #compute} makes together.
     *
     * <p>{@code compute} differs from the published rule in two ways — it keys
     * values by pattern identity, and it SUMS the sequences that spell the same
     * pattern. The first is a correction; the second is stronger than anything
     * the paper specifies, and it moves the threshold in the opposite
     * direction. Measuring the two together cannot say which did what.</p>
     *
     * <p>This variant keeps the keying and takes the maximum instead of the
     * sum, which is still a valid lower bound: a pattern reaching utility u in
     * one sequence reaches at least u in the database.</p>
     */
    static int computeDedupNoSum(List<SequenceData> database,
                                 List<Integer> sequenceUtils,
                                 Map<Integer, Integer> itemUtilities,
                                 int k) {
        PriorityQueue<Integer> topK = new PriorityQueue<>(k + 1);
        for (int u : itemUtilities.values()) offer(topK, u, k);
        twoSequenceUtilities(database).offerValuesInto(topK, k);
        for (int u : longSequenceUtilities(database, sequenceUtils, false).values())
            offer(topK, u, k);
        return (topK.size() >= k) ? Math.max(0, topK.peek()) : 0;
    }

    /**
     * Utility lower bounds for q-sequences of length three or more, keyed by
     * the pattern the sequence spells out (items per itemset, utilities
     * ignored) so that repeated patterns are accumulated rather than counted
     * as separate patterns.
     */
    private static Map<String, Integer> longSequenceUtilities(
            List<SequenceData> database, List<Integer> sequenceUtils, boolean sum) {
        Map<String, Integer> totals = new HashMap<>();
        if (sequenceUtils == null) return totals;
        // sequenceUtils is indexed by load order, which SequenceData.id preserves.
        for (SequenceData seq : database) {
            if (seq.id < 0 || seq.id >= sequenceUtils.size()) continue;
            int nbCols = seq.matrixItemUtility[0].length;
            int nbRows = seq.itemNames.length;
            // Count first: sequences of fewer than three items are already
            // ranked exactly as 1- or 2-sequences, and on short-sequence data
            // (Yoochoose averages 1.13 itemsets) they are the vast majority —
            // building a key for them and discarding it dominated this pass.
            int items = 0;
            for (int c = 0; c < nbCols && items < 3; c++) {
                for (int r = 0; r < nbRows; r++) {
                    if (seq.matrixItemUtility[r][c] > 0 && ++items >= 3) break;
                }
            }
            if (items < 3) continue;

            StringBuilder key = new StringBuilder();
            for (int c = 0; c < nbCols; c++) {
                boolean any = false;
                for (int r = 0; r < nbRows; r++) {
                    if (seq.matrixItemUtility[r][c] > 0) {
                        if (any) key.append(',');
                        key.append(seq.itemNames[r]);
                        any = true;
                    }
                }
                if (any) key.append('|');
            }
            totals.merge(key.toString(), sequenceUtils.get(seq.id),
                         sum ? Integer::sum : Integer::max);
        }
        return totals;
    }

    private static void offer(PriorityQueue<Integer> heap, int value, int k) {
        heap.offer(value);
        if (heap.size() > k) heap.poll();
    }

    /**
     * Exact utilities of every 2-sequence occurring in the database.
     *
     * <p>Key layout: {@code (long) itemA << 32 | itemB}, in a separate map per
     * extension type, so {@code <(a b)>} and {@code <(a)(b)>} never collide.
     * Only pairs that actually co-occur are ever inserted.</p>
     */
    // Reused across sequences: the pair pass runs once per sequence and would
    // otherwise allocate three arrays each time (234k sequences on Yoochoose).
    private static int[] bufOcc = new int[0], bufBest = new int[0], bufSeen = new int[0];

    private static int[] scratch(int n) {
        if (bufOcc.length < n) bufOcc = new int[Math.max(n, 64)];
        return bufOcc;
    }

    private static int[] scratchBest(int n) {
        if (bufBest.length < n) { bufBest = new int[Math.max(n, 64)]; Arrays.fill(bufBest, -1); }
        return bufBest;   // invariant: every entry is -1 on entry, restored on exit
    }

    private static int[] scratchSeen(int n) {
        if (bufSeen.length < n) bufSeen = new int[Math.max(n, 64)];
        return bufSeen;
    }

    private static int[] bufStart = new int[0], bufByCol = new int[0], bufFill = new int[0];

    private static int[] scratchStart(int n) {
        if (bufStart.length < n) bufStart = new int[Math.max(n, 64)];
        return bufStart;
    }

    private static int[] scratchByCol(int n) {
        if (bufByCol.length < n) bufByCol = new int[Math.max(n, 64)];
        return bufByCol;
    }

    private static int[] scratchFill(int n) {
        if (bufFill.length < n) bufFill = new int[Math.max(n, 64)];
        return bufFill;
    }

    private static LongIntMap twoSequenceUtilities(List<SequenceData> database) {
        LongIntMap totals = new LongIntMap(1 << 16);
        LongIntMap perSeq = new LongIntMap(1 << 8);

        for (SequenceData seq : database) {
            perSeq.clear();
            int[] names = seq.itemNames;
            int nbItems = names.length;
            if (nbItems == 0) continue;
            int nbCols = seq.matrixItemUtility[0].length;

            // One left-to-right sweep serves both extension types. The rows
            // occupied in a column are gathered ONCE and reused, because a
            // dense rescan costs nbCols * nbItems per pass — on the widest
            // Kosarak sequence that is 370k reads per pass, for data whose
            // itemsets almost always hold a single item.
            //
            //   bestBefore[r] = best utility of item r strictly before the
            //                   current column (-1 = not seen yet)
            //   seen[0..nbSeen) = the rows for which it is defined, so only
            //                   rows that can actually start a pair are visited
            //
            // I-type pairs come from within the current column; S-type pairs
            // join `seen` to the current column. Both orders (a,b) and (b,a)
            // are distinct S-patterns, and a == b is legitimate (an item
            // repeating later), hence the diagonal is included.
            // The occupied rows of each column are materialised by a counting
            // sort over two ROW-MAJOR passes. Gathering a column by scanning
            // matrixItemUtility[r][c] with c outermost would touch a different
            // row array on every read; on the widest Kosarak sequence (608
            // rows x 609 columns) that strided pattern cost more than all the
            // pair arithmetic combined.
            int[] bestBefore = scratchBest(nbItems);
            int[] seen = scratchSeen(nbItems);
            int nbSeen = 0;

            int[] colStart = scratchStart(nbCols + 1);
            java.util.Arrays.fill(colStart, 0, nbCols + 1, 0);
            for (int r = 0; r < nbItems; r++) {
                int[] row = seq.matrixItemUtility[r];
                for (int c = 0; c < nbCols; c++) if (row[c] > 0) colStart[c + 1]++;
            }
            for (int c = 0; c < nbCols; c++) colStart[c + 1] += colStart[c];
            int nbOcc = colStart[nbCols];
            int[] byCol = scratchByCol(nbOcc);
            int[] fill = scratchFill(nbCols + 1);
            System.arraycopy(colStart, 0, fill, 0, nbCols + 1);
            // Rows are visited in ascending order, so each column's slice comes
            // out ascending too — which the I-type pairing below relies on.
            for (int r = 0; r < nbItems; r++) {
                int[] row = seq.matrixItemUtility[r];
                for (int c = 0; c < nbCols; c++) if (row[c] > 0) byCol[fill[c]++] = r;
            }

            for (int c = 0; c < nbCols; c++) {
                int from = colStart[c], to = colStart[c + 1];
                int n = to - from;
                if (n == 0) continue;
                int[] occ = byCol;
                int base = from;

                // I-type: rows are sorted ascending by item id, so i < j
                // enumerates each unordered pair once in canonical order.
                for (int i = 0; i < n; i++) {
                    int r1 = occ[base + i];
                    int u1 = seq.matrixItemUtility[r1][c];
                    for (int j = i + 1; j < n; j++) {
                        int r2 = occ[base + j];
                        perSeq.keepMax(key(names[r1], names[r2], true),
                                       u1 + seq.matrixItemUtility[r2][c]);
                    }
                }

                // S-type: everything seen strictly earlier, joined to this column.
                for (int i = 0; i < n; i++) {
                    int r2 = occ[base + i];
                    int u2 = seq.matrixItemUtility[r2][c];
                    for (int sIdx = 0; sIdx < nbSeen; sIdx++) {
                        int r1 = seen[sIdx];
                        perSeq.keepMax(key(names[r1], names[r2], false),
                                       bestBefore[r1] + u2);
                    }
                }

                // Only now does this column become "before" the next one.
                for (int i = 0; i < n; i++) {
                    int r = occ[base + i];
                    int u = seq.matrixItemUtility[r][c];
                    if (bestBefore[r] < 0) seen[nbSeen++] = r;
                    if (u > bestBefore[r]) bestBefore[r] = u;
                }
            }
            for (int s = 0; s < nbSeen; s++) bestBefore[seen[s]] = -1;   // reset touched only

            perSeq.addInto(totals);
        }
        return totals;
    }

    /**
     * Open-addressing long-to-int map. The pair accumulation runs tens of
     * millions of times, where {@code HashMap<Long,Integer>} would box both
     * key and value on every operation.
     */
    private static final class LongIntMap {
        private long[] keys;
        private int[] vals;
        private boolean[] used;
        private int[] dirty = new int[64];
        private int nbDirty;
        private int size, mask, shift;

        LongIntMap(int capacity) { alloc(capacity); }

        private void alloc(int capacity) {
            int cap = Integer.highestOneBit(Math.max(16, capacity) - 1) << 1;
            keys = new long[cap];
            vals = new int[cap];
            used = new boolean[cap];
            mask = cap - 1;
            // Fibonacci hashing takes the TOP log2(cap) bits, so the shift has
            // to follow the capacity. See the correctness note in slot().
            shift = 64 - Integer.numberOfTrailingZeros(cap);
            size = 0;
        }

        private int slot(long key) {
            // Fibonacci hashing on the HIGH bits: the low bits of a 64-bit
            // product are barely mixed, which degenerates linear probing into
            // long collision runs on structured keys like (itemA, itemB).
            //
            // FIXED 2026-08-14. This read `(int) (h >>> 40) & mask`, a constant
            // 40 that yields 24 bits and therefore confines every home slot to
            // the first 2^24 = 16,777,216 entries however large the table
            // grows. Below that many distinct pairs nothing is visibly wrong,
            // which is why eight datasets never showed it. Above it the home
            // region saturates and each insertion walks the resulting cluster,
            // so the cost turns quadratic exactly where the table is largest.
            // Measured on this database, same code, only the bit range differs:
            //
            //   e_shop           87,075 pairs   0.28 s  vs 0.25 s   (no effect)
            //   MicroblogPCU 18,710,898 pairs   did not finish in 200 s
            //                                   vs 1.09 s
            //
            // The shift now follows the capacity, so the index uses exactly the
            // log2(cap) top bits and needs no mask.
            long h = key * 0x9E3779B97F4A7C15L;
            int i = (int) (h >>> shift);
            while (used[i] && keys[i] != key) i = (i + 1) & mask;
            return i;
        }

        void keepMax(long key, int value) {
            int i = slot(key);
            if (used[i]) {
                if (value > vals[i]) vals[i] = value;
            } else {
                put(i, key, value);
            }
        }

        void add(long key, int value) {
            int i = slot(key);
            if (used[i]) vals[i] += value;
            else put(i, key, value);
        }

        private void put(int i, long key, int value) {
            used[i] = true; keys[i] = key; vals[i] = value; size++;
            if (nbDirty == dirty.length) dirty = Arrays.copyOf(dirty, dirty.length * 2);
            dirty[nbDirty++] = i;
            if (size * 2 > keys.length) rehash();
        }

        private void rehash() {
            long[] ok = keys; int[] ov = vals; boolean[] ou = used;
            alloc(ok.length * 2);
            nbDirty = 0;
            for (int i = 0; i < ok.length; i++) {
                if (ou[i]) {
                    int j = slot(ok[i]);
                    used[j] = true; keys[j] = ok[i]; vals[j] = ov[i]; size++;
                    if (nbDirty == dirty.length) dirty = Arrays.copyOf(dirty, dirty.length * 2);
                    dirty[nbDirty++] = j;
                }
            }
        }

        /** Clears only the slots actually written, not the whole table: this
         *  runs once per sequence and the table is far larger than a typical
         *  sequence's pair count. */
        void clear() {
            for (int i = 0; i < nbDirty; i++) used[dirty[i]] = false;
            nbDirty = 0;
            size = 0;
        }

        /** Merges via the dirty list, NOT a full table scan: this map is
         *  reused across sequences and keeps the capacity of the largest one
         *  ever seen, so scanning the whole table per sequence would cost
         *  (sequences x peak capacity) — billions of reads on data with one
         *  very long sequence among many short ones. */
        void addInto(LongIntMap target) {
            for (int i = 0; i < nbDirty; i++) {
                int slot = dirty[i];
                if (used[slot]) target.add(keys[slot], vals[slot]);
            }
        }

        /** Rank every stored value without boxing keys or values. */
        void offerValuesInto(PriorityQueue<Integer> heap, int k) {
            for (int i = 0; i < keys.length; i++) if (used[i]) offer(heap, vals[i], k);
        }

        int distinctKeys() { return size; }
    }

    /** I-type keys are tagged so they cannot collide with S-type keys. */
    private static long key(int a, int b, boolean iType) {
        long base = ((long) a << 32) | (b & 0xffffffffL);
        return iType ? base : ~base;
    }

}
