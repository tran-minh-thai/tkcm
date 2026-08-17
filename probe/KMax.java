import java.io.File;
/**
 * k_max — the largest k a configuration completes at, per dataset.
 *
 * <p>Turns an infeasibility row into a comparable number. Kosarak10k already
 * showed the boundary moves: nothing completed at k=16 until the fixpoint, and
 * then it took 0.69 s. Reporting "infeasible" hides that; reporting k_max does
 * not, and the distance a contribution moves it is the measurement.</p>
 *
 * <p>Smaller k means a HIGHER threshold and so a smaller tree, which is why the
 * sweep stops at the first k that fails: no larger k can succeed where a
 * smaller one did not.</p>
 *
 * <p>Prints the SUR seed beside the threshold actually reached. A run that ends
 * with minUtility far above the seed found good patterns and still could not
 * certify them, which is a different failure from one that never raised the
 * threshold at all.</p>
 *
 * Usage: java KMax &lt;dataset&gt; &lt;k1,k2,...&gt; &lt;budgetSeconds&gt;
 */
public class KMax {
    public static void main(String[] a) throws Exception {
        String ds = a[0];
        int budget = Integer.parseInt(a[2]);
        for (String ks : a[1].split(",")) {
            int k = Integer.parseInt(ks);
            final AlgoTKCM e = new AlgoTKCM(true, true, true, true, true);
            File out = File.createTempFile("kmax", ".txt");
            out.deleteOnExit();
            final boolean[] done = {false};
            Thread t = new Thread(() -> {
                try { e.runAlgorithm("datasets/" + ds + ".txt", out.getPath(), k); done[0] = true; }
                catch (Throwable x) { System.out.println("   " + x); }
            });
            t.setDaemon(true);
            long t0 = System.nanoTime();
            t.start();
            t.join(budget * 1000L);
            System.out.printf("  %-22s k=%-4d %-9s %7.1f s   sur=%,-10d minUtil=%,-11d proj=%,d%n",
                    ds, k, done[0] ? "COMPLETED" : "PARTIAL",
                    (System.nanoTime() - t0) / 1e9, e.surTheta, e.minUtility, e.projections);
            System.out.flush();
            if (!done[0]) break;
        }
        Runtime.getRuntime().halt(0);
    }
}
