import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Ground-truth correctness test for AlgoTKUS and AlgoTKCM.
 *
 * Method:
 *   1. Run AlgoUSpan with minUtility = 1 to enumerate ALL high-utility
 *      sequential patterns (exhaustive enumeration = ground truth).
 *   2. Derive the exact top-k set using the same tie rule as updateTKList
 *      (whole utility buckets are kept; a bucket is dropped only when >= k
 *      patterns have strictly higher utility).
 *   3. Run AlgoTKUS and every AlgoTKCM configuration at the same k and diff
 *      against the ground truth (pattern set AND utility values).
 *
 * Usage:
 *   java VerifyTopK <dataset> <k1> [k2 ...]
 * Exit code 0 iff every algorithm reproduces the exact top-k at every k.
 *
 * Only use datasets small enough for exhaustive USpan enumeration
 * (uspan.txt, HUSRM.txt, the counterexample files in test/data/).
 */
public class VerifyTopK {

    static Map<String, Integer> readPatterns(String path) throws IOException {
        Map<String, Integer> m = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(path))) {
            line = line.trim();
            if (line.isEmpty()) continue;
            int i = line.lastIndexOf(':');
            m.put(line.substring(0, i), Integer.parseInt(line.substring(i + 1).trim()));
        }
        return m;
    }

    /** Exact top-k under the "keep whole tie buckets" rule of updateTKList. */
    static Map<String, Integer> exactTopK(Map<String, Integer> all, int k) {
        TreeMap<Integer, List<String>> byU = new TreeMap<>(Collections.reverseOrder());
        for (Map.Entry<String, Integer> e : all.entrySet())
            byU.computeIfAbsent(e.getValue(), x -> new ArrayList<>()).add(e.getKey());
        Map<String, Integer> out = new HashMap<>();
        int above = 0;
        for (Map.Entry<Integer, List<String>> e : byU.entrySet()) {
            if (above >= k) break;
            for (String p : e.getValue()) out.put(p, e.getKey());
            above += e.getValue().size();
        }
        return out;
    }

    static boolean check(String name, Map<String, Integer> want, Map<String, Integer> got) {
        List<String> missing = new ArrayList<>(), extra = new ArrayList<>(), wrong = new ArrayList<>();
        for (Map.Entry<String, Integer> e : want.entrySet()) {
            Integer g = got.get(e.getKey());
            if (g == null) missing.add(e.getKey() + ":" + e.getValue());
            else if (!g.equals(e.getValue())) wrong.add(e.getKey() + " want " + e.getValue() + " got " + g);
        }
        for (String p : got.keySet()) if (!want.containsKey(p)) extra.add(p + ":" + got.get(p));

        if (missing.isEmpty() && extra.isEmpty() && wrong.isEmpty()) {
            System.out.println("    [" + name + "] OK");
            return true;
        }
        System.out.println("    [" + name + "] FAIL  missing=" + missing.size()
                + " extra=" + extra.size() + " wrongUtil=" + wrong.size());
        for (String s : missing.subList(0, Math.min(5, missing.size()))) System.out.println("        MISSING " + s);
        for (String s : extra.subList(0, Math.min(5, extra.size())))     System.out.println("        EXTRA   " + s);
        for (String s : wrong.subList(0, Math.min(5, wrong.size())))     System.out.println("        WRONGU  " + s);
        return false;
    }

    public static void main(String[] args) throws Exception {
        String ds = args[0];
        String tmp = Files.createTempDirectory("verifytopk").toString();

        AlgoUSpan uspan = new AlgoUSpan();
        uspan.runAlgorithm(ds, tmp + "/gt.txt", 1);
        Map<String, Integer> all = readPatterns(tmp + "/gt.txt");
        System.out.println("== " + ds + "  (ground truth: " + all.size() + " HUSPs) ==");

        boolean allOk = true;
        for (int a = 1; a < args.length; a++) {
            int k = Integer.parseInt(args[a]);
            Map<String, Integer> want = exactTopK(all, k);
            System.out.println("  k=" + k + "  (exact top-k size " + want.size() + ")");

            AlgoTKUS tkus = new AlgoTKUS();
            tkus.runAlgorithm(ds, tmp + "/tkus.txt", k);
            allOk &= check("TKUS    ", want, readPatterns(tmp + "/tkus.txt"));

            // Unified engine: every flag combination must reproduce the exact
            // top-k (ablation flags never change the result set — rule 2.3.8).
            for (int m = 0; m < 64; m++) {
                AlgoTKCM eng = new AlgoTKCM((m & 1) != 0, (m & 2) != 0,
                                              (m & 4) != 0, (m & 8) != 0, (m & 16) != 0,
                                              (m & 32) != 0);
                eng.runAlgorithm(ds, tmp + "/eng.txt", k);
                allOk &= check("eng-" + eng.configName(), want, readPatterns(tmp + "/eng.txt"));
            }
        }
        if (!allOk) System.exit(1);
    }
}
