import java.io.*; import java.nio.file.*; import java.security.MessageDigest; import java.util.*;
/**
 * The cross-family check, in machine-invariant quantities.
 *
 * <p>Top-k mining discovers its threshold; threshold mining is handed one. Run
 * the engine at k, take the threshold it converged to, and run USpan at exactly
 * that value: both must emit the same set, because the top-k answer is by
 * definition every pattern whose utility reaches the k-th best.</p>
 *
 * <p>This is the only check in the suite that crosses algorithm families — the
 * two implementations share no mining code — and it compares hashes, so it needs
 * no particular machine.</p>
 *
 * Usage: java CrossFamily &lt;dataset&gt; &lt;k1,k2,...&gt;
 */
public class CrossFamily {
    static String sha(String p) throws Exception {
        List<String> l = Files.readAllLines(Paths.get(p)); Collections.sort(l);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String s : l) { md.update(s.getBytes("UTF-8")); md.update((byte) '\n'); }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString().substring(0, 16);
    }
    public static void main(String[] a) throws Exception {
        int bad = 0;
        String runId = a.length > 2 ? a[2] : "xfam-" + a[0];
        for (String ks : a[1].split(",")) {
            int k = Integer.parseInt(ks);
            String in = "datasets/" + a[0] + ".txt";

            AlgoTKCM eng = new AlgoTKCM(true, true, true, true, true);
            File oe = File.createTempFile("xfam", ".txt"); oe.deleteOnExit();
            long t0 = System.nanoTime();
            eng.runAlgorithm(in, oe.getPath(), k);
            double se = (System.nanoTime() - t0) / 1e9;
            int theta = eng.minUtility;          // derived OUTSIDE the oracle's run

            AlgoUSpan orc = new AlgoUSpan();
            File oo = File.createTempFile("xfam", ".txt"); oo.deleteOnExit();
            long t1 = System.nanoTime();
            orc.runAlgorithm(in, oo.getPath(), theta);
            double so = (System.nanoTime() - t1) / 1e9;

            String he = sha(oe.getPath()), ho = sha(oo.getPath());
            long pe = Files.readAllLines(oe.toPath()).stream().filter(x -> !x.isBlank()).count();
            long po = Files.readAllLines(oo.toPath()).stream().filter(x -> !x.isBlank()).count();
            boolean ok = he.equals(ho);
            if (!ok) bad++;
            InvariantLog.row(runId, a[0], k, "eng-w1s1c1m1f1", "OK", pe, theta, he,
                    "projections=" + eng.projections);
            InvariantLog.row(runId, a[0], k, "uspan-oracle", "OK", po, theta, ho, "");
            System.out.printf("  %-10s k=%-5d theta=%,-10d engine %s (%,d pat, %.1fs)  oracle %s (%,d pat, %.1fs)  %s%n",
                    a[0], k, theta, he, pe, se, ho, po, so, ok ? "SAME SET" : "*** DIFFER ***");
            System.out.flush();
            oe.delete(); oo.delete();
        }
        System.out.printf("%n%s%n", bad == 0
                ? "Top-k and threshold mining agree on every cell."
                : bad + " cells disagree.");
        Runtime.getRuntime().halt(bad == 0 ? 0 : 1);
    }
}
