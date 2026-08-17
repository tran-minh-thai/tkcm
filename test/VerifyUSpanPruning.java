import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

/**
 * Differential test of USpan's own depth pruning.
 *
 * USpan takes minUtility as an input and prunes with it. Running it at
 * {@code minUtility = 1} makes the depth test {@code totalUtility +
 * totalRemainingUtility >= 1} vacuous, because any pattern that occurs has
 * positive utility — so that run enumerates every high-utility pattern and is
 * what the project uses as ground truth.
 *
 * This test compares, for a range of thresholds θ:
 *   A. USpan run at minUtility = θ
 *   B. USpan run at minUtility = 1, then filtered to utility ≥ θ
 * If the pruning is sound, A == B exactly. A pattern present in B but missing
 * from A is a pattern the pruning discarded although it qualifies.
 *
 * Usage: java VerifyUSpanPruning &lt;dataset&gt; [theta ...]
 * Exit code 0 iff every threshold agrees.
 */
public class VerifyUSpanPruning {

    static Map<String, Integer> read(String path) throws IOException {
        Map<String, Integer> m = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(path))) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int i = line.lastIndexOf(':');
            m.put(line.substring(0, i), Integer.parseInt(line.substring(i + 1).trim()));
        }
        return m;
    }

    public static void main(String[] args) throws Exception {
        String ds = args[0];
        String tmp = Files.createTempDirectory("uspanprune").toString();

        new AlgoUSpan().runAlgorithm(ds, tmp + "/all.txt", 1);
        Map<String, Integer> all = read(tmp + "/all.txt");
        System.out.println("== " + ds + " : " + all.size() + " patterns at minUtility=1 ==");

        boolean ok = true;
        for (int a = 1; a < args.length; a++) {
            int theta = Integer.parseInt(args[a]);

            Map<String, Integer> expected = new HashMap<>();
            for (Map.Entry<String, Integer> e : all.entrySet())
                if (e.getValue() >= theta) expected.put(e.getKey(), e.getValue());

            new AlgoUSpan().runAlgorithm(ds, tmp + "/pruned.txt", theta);
            Map<String, Integer> got = read(tmp + "/pruned.txt");

            TreeSet<String> missing = new TreeSet<>(expected.keySet());
            missing.removeAll(got.keySet());
            TreeSet<String> extra = new TreeSet<>(got.keySet());
            extra.removeAll(expected.keySet());

            if (missing.isEmpty() && extra.isEmpty()) {
                System.out.printf("  theta=%-8d expected %-5d got %-5d  OK%n",
                        theta, expected.size(), got.size());
            } else {
                ok = false;
                System.out.printf("  theta=%-8d expected %-5d got %-5d  MISSING %d, EXTRA %d%n",
                        theta, expected.size(), got.size(), missing.size(), extra.size());
                int shown = 0;
                for (String p : missing) {
                    System.out.println("      pruned away: " + p + ":" + expected.get(p));
                    if (++shown == 5) break;
                }
                for (String p : extra) {
                    System.out.println("      unexpected : " + p + ":" + got.get(p));
                    break;
                }
            }
        }
        if (!ok) System.exit(1);
    }
}
