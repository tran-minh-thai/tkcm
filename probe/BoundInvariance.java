import java.io.*; import java.nio.file.*; import java.security.MessageDigest; import java.util.*;
/**
 * The subsumption claim, measured with machine-invariant quantities only.
 *
 * <p>The claim is that refining the bounds does not change the search tree: every
 * combination of the width, split-gate and co-occurrence flags visits the same
 * nodes and emits the same patterns. Both are counters, so this needs no
 * particular machine — only wall-clock comparisons do.</p>
 *
 * <p>Reports each combination's projections and output hash against the first,
 * and exits non-zero on any disagreement.</p>
 *
 * Usage: java BoundInvariance &lt;dataset&gt; &lt;k1,k2,...&gt;
 */
public class BoundInvariance {
    static String sha(String path) throws Exception {
        List<String> l = Files.readAllLines(Paths.get(path));
        Collections.sort(l);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String s : l) { md.update(s.getBytes("UTF-8")); md.update((byte) '\n'); }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString().substring(0, 16);
    }
    public static void main(String[] a) throws Exception {
        int bad = 0;
        String runId = a.length > 2 ? a[2] : "bounds-" + a[0];
        for (String ks : a[1].split(",")) {
            int k = Integer.parseInt(ks);
            String refSha = null; long refProj = -1;
            System.out.printf("%n%s k=%d%n", a[0], k);
            for (int w = 0; w <= 1; w++) for (int s = 0; s <= 1; s++) for (int c = 0; c <= 1; c++) {
                AlgoTKCM e = new AlgoTKCM(w == 1, s == 1, c == 1, true, false);
                File o = File.createTempFile("bound", ".txt"); o.deleteOnExit();
                e.runAlgorithm("datasets/" + a[0] + ".txt", o.getPath(), k);
                String h = sha(o.getPath()); o.delete();
                if (refSha == null) { refSha = h; refProj = e.projections; }
                boolean ok = h.equals(refSha) && e.projections == refProj;
                if (!ok) bad++;
                InvariantLog.row(runId, a[0], k, String.format("eng-w%ds%dc%dm1f0", w, s, c),
                        "OK", 0, 0, h, "projections=" + e.projections);
                System.out.printf("   w%ds%dc%dm1f0  proj=%-9d sha=%s  %s%n",
                        w, s, c, e.projections, h, ok ? "same" : "*** DIFFERS ***");
            }
        }
        System.out.printf("%n%s%n", bad == 0
                ? "Every bound combination visits the same nodes and emits the same patterns."
                : bad + " combinations disagree.");
        Runtime.getRuntime().halt(bad == 0 ? 0 : 1);
    }
}
