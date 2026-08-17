import java.io.*; import java.nio.file.*; import java.security.MessageDigest; import java.util.*;
/**
 * The representation claim, in machine-invariant quantities.
 *
 * <p>Pairwise and column-merged chains must emit the same patterns and visit the
 * same nodes; the merged scan must do strictly less work; and the redundancy
 * factor measured at one projection step must compound over the whole run. All
 * four are counters or hashes, so they need no particular machine — only the
 * wall-clock and memory ratios do.</p>
 *
 * Usage: java Representation &lt;dataset&gt; &lt;k1,k2,...&gt;
 */
public class Representation {
    static String sha(String p) throws Exception {
        List<String> l = Files.readAllLines(Paths.get(p)); Collections.sort(l);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String s : l) { md.update(s.getBytes("UTF-8")); md.update((byte) '\n'); }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString().substring(0, 16);
    }
    /** Engine plus the file it wrote — kept here rather than on the engine, so
     *  that reading counters never requires touching src/. */
    record Run(AlgoTKCM engine, String output) {}

    static Run run(String ds, int k, boolean merged) throws Exception {
        AlgoTKCM e = new AlgoTKCM(true, true, true, merged, false);
        File o = File.createTempFile("repr", ".txt"); o.deleteOnExit();
        e.runAlgorithm("datasets/" + ds + ".txt", o.getPath(), k);
        return new Run(e, o.getPath());
    }
    public static void main(String[] a) throws Exception {
        String runId = a.length > 2 ? a[2] : "repr-" + a[0];
        for (String ks : a[1].split(",")) {
            int k = Integer.parseInt(ks);
            Run rp = run(a[0], k, false), rm = run(a[0], k, true);
            AlgoTKCM p = rp.engine(), m = rm.engine();
            String hp = sha(rp.output()), hm = sha(rm.output());
            double pbar = m.scanStepsS > 0 ? m.dominatedPairs / (double) m.scanStepsS : Double.NaN;
            double glob = m.scanStepsS > 0 ? p.scanStepsS / (double) m.scanStepsS : Double.NaN;
            System.out.printf("%n%s k=%d%n", a[0], k);
            System.out.printf("   exactness      hash %s vs %s   %s%n", hp, hm, hp.equals(hm) ? "SAME" : "*** DIFFER ***");
            System.out.printf("   same tree      projections %,d vs %,d   %s%n", p.projections, m.projections,
                    p.projections == m.projections ? "SAME" : "*** DIFFER ***");
            System.out.printf("   S-scan work    %,d -> %,d   = %.1fx less%n", p.scanStepsS, m.scanStepsS, glob);
            System.out.printf("   chain elements %,d -> %,d   peak %,d -> %,d%n",
                    p.chainElements, m.chainElements, p.peakChainElements, m.peakChainElements);
            System.out.printf("   p-bar (local)  %.3f   =>  compounding %.1fx over the run%n", pbar, glob / pbar);
            InvariantLog.row(runId, a[0], k, "eng-w1s1c1m0f0", "OK", 0, p.minUtility, hp,
                    "projections=" + p.projections + ";scanStepsS=" + p.scanStepsS
                  + ";chainElements=" + p.chainElements + ";peakChainElements=" + p.peakChainElements);
            InvariantLog.row(runId, a[0], k, "eng-w1s1c1m1f0", "OK", 0, m.minUtility, hm,
                    "projections=" + m.projections + ";scanStepsS=" + m.scanStepsS
                  + ";dominatedPairs=" + m.dominatedPairs
                  + ";chainElements=" + m.chainElements + ";peakChainElements=" + m.peakChainElements);
            new File(rp.output()).delete(); new File(rm.output()).delete();
        }
        Runtime.getRuntime().halt(0);
    }
}
