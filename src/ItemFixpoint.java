import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Least-fixpoint item filter — the sequential counterpart of EFIM's local
 * utility pruning, and the sound realisation of the "promising-item filtered
 * remaining utility" that earlier versions of this project documented but
 * never implemented.
 *
 * <h3>Rule</h3>
 * Write {@code u_F(s)} for the utility of sequence {@code s} counting only
 * items in a set {@code F}, and
 * <pre>
 *   SWU_F(a) = sum over sequences containing a of u_F(s)
 * </pre>
 * Starting from all items, repeatedly delete every item with
 * {@code SWU_F(a) < theta} and recompute; the set shrinks monotonically, so
 * the iteration reaches a least fixpoint.
 *
 * <h3>Soundness</h3>
 * Claim: if {@code a} is deleted, no pattern containing {@code a} has utility
 * at least {@code theta}.
 *
 * <p>By induction on the round in which {@code a} is deleted. In round 1 the
 * test is the ordinary sequence-weighted bound: a pattern P containing
 * {@code a} occurs only in sequences containing {@code a}, and its utility in
 * such a sequence is at most the sequence's utility, so
 * {@code u(P) <= SWU(a) < theta}. In round n, every item outside
 * {@code F_(n-1)} has already been shown to appear in no pattern of utility
 * at least {@code theta}; hence such a pattern P containing {@code a}
 * satisfies {@code P subset of F_(n-1)}, so its utility in a sequence s is at
 * most {@code u_F(n-1)(s)}, giving
 * {@code u(P) <= SWU_F(n-1)(a) < theta}. &#8718;</p>
 *
 * <p>Applied with {@code theta} equal to the SUR threshold, which is a lower
 * bound on the utility of every top-k pattern, so no top-k pattern is lost.
 * The threshold rises during mining, so re-running this filter later would
 * remove more items still; it is applied once, before mining, where the whole
 * database can be rebuilt around the surviving vocabulary.</p>
 */
final class ItemFixpoint {

    private ItemFixpoint() {}

    /** Diagnostics for one run, reported through the engine's counters. */
    static final class Result {
        final Set<Integer> items;
        final int initialItems, rounds;
        Result(Set<Integer> items, int initialItems, int rounds) {
            this.items = items; this.initialItems = initialItems; this.rounds = rounds;
        }
    }

    /**
     * @param database loaded sequences (unfiltered)
     * @param theta    utility threshold every top-k pattern is known to reach
     * @return the surviving item set and how it was reached
     */
    static Result compute(List<SequenceData> database, int theta) {
        Set<Integer> survivors = new HashSet<>();
        for (SequenceData seq : database) {
            for (int item : seq.itemNames) survivors.add(item);
        }
        int initial = survivors.size();
        if (theta <= 0) return new Result(survivors, initial, 0);

        // Per-sequence row utilities are recomputed each round because the
        // surviving set changes; each round is one pass over the occupied
        // cells, and the number of rounds is bounded by |items| but is small
        // in practice (single digits on every dataset measured).
        int rounds = 0;
        while (true) {
            rounds++;
            java.util.Map<Integer, Long> swu = new java.util.HashMap<>();
            for (SequenceData seq : database) {
                long uF = 0;
                int[][] util = seq.matrixItemUtility;
                int nbCols = util[0].length;
                for (int r = 0; r < seq.itemNames.length; r++) {
                    if (!survivors.contains(seq.itemNames[r])) continue;
                    int[] row = util[r];
                    for (int c = 0; c < nbCols; c++) uF += row[c];
                }
                if (uF == 0) continue;
                for (int r = 0; r < seq.itemNames.length; r++) {
                    int item = seq.itemNames[r];
                    if (!survivors.contains(item)) continue;
                    swu.merge(item, uF, Long::sum);
                }
            }
            Set<Integer> next = new HashSet<>();
            for (java.util.Map.Entry<Integer, Long> e : swu.entrySet()) {
                if (e.getValue() >= theta) next.add(e.getKey());
            }
            boolean stable = next.size() == survivors.size();
            survivors = next;
            if (stable || survivors.isEmpty()) break;
        }
        return new Result(survivors, initial, rounds);
    }
}
