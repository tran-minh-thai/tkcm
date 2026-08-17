import java.util.*;
/**
 * A cardinality pruning rule, measured before it is implemented.
 *
 * <p>Utility sums over sequences, so a pattern reaching threshold t must occur
 * in at least ceil(t / m) of them, where m is the most any single sequence can
 * contribute. Every extension Q of P contains some item b and satisfies
 * S(Q) subset of S(P) intersect S(b), so if no remaining item occurs in enough
 * sequences, the whole subtree is empty.</p>
 *
 * <p>This is not of the form u(prefix) + remaining: it constrains the NUMBER of
 * sequences rather than their utility, which is why the analysis that rules out
 * the PEU family does not apply to it.</p>
 *
 * <p>Measured here at the root, where the existing bounds are weakest. Reports
 * the required support against the distribution of item supports: the fraction
 * of items falling below it is the fraction the rule would eliminate outright.</p>
 *
 * Usage: java Cardinality &lt;dataset&gt; &lt;threshold&gt;
 */
public class Cardinality {
    public static void main(String[] a) throws Exception {
        String ds = a[0];
        long theta = Long.parseLong(a[1]);
        Dataset.DatasetResult res = new Dataset(false, 2000).loadDataset("datasets/" + ds + ".txt", 0);
        List<SequenceData> db = res.sequenceDatabase;

        long m = 0, total = 0;
        Map<Integer, Integer> support = new HashMap<>();
        for (SequenceData s : db) {
            long su = 0;
            for (int r = 0; r < s.itemNames.length; r++) {
                boolean present = false;
                for (int u : s.matrixItemUtility[r]) { su += u; if (u > 0) present = true; }
                if (present) support.merge(s.itemNames[r], 1, Integer::sum);
            }
            total += su;
            if (su > m) m = su;          // the most one sequence can ever contribute
        }
        long need = (long) Math.ceil(theta / (double) m);
        int[] sup = support.values().stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(sup);
        long below = Arrays.stream(sup).filter(x -> x < need).count();

        System.out.printf("%-22s theta=%,-10d  sequences=%,d  total utility=%,d%n", ds, theta, db.size(), total);
        System.out.printf("   richest sequence contributes m = %,d%n", m);
        System.out.printf("   a pattern reaching theta must occur in >= %,d sequences (%.2f%% of the database)%n",
                need, 100.0 * need / db.size());
        System.out.printf("   item supports: min %d, median %d, p90 %d, max %d, distinct items %,d%n",
                sup[0], sup[sup.length / 2], sup[(int) (sup.length * 0.9)], sup[sup.length - 1], sup.length);
        System.out.printf("   items BELOW the required support: %,d of %,d = %.2f%%  <-- the rule eliminates these outright%n%n",
                below, sup.length, 100.0 * below / sup.length);
    }
}
