import java.io.*; import java.nio.file.*; import java.util.*;
/**
 * Items removed by each round of the vocabulary fixpoint.
 *
 * <p>The per-round counts are not a column of the benchmark rows, so a figure
 * needing them had them typed into the generator by hand. Those hand-typed
 * values disagreed with the engine: they summed to 10,080 removals over ten
 * rounds where the engine's own counters report 10,054 removals over eighteen.
 * Hand-typed numbers in a generator are the same fault as hand-typed numbers in
 * prose, one level further from view.</p>
 *
 * <p>This recomputes them from the definition and then CHECKS the recomputation
 * against {@link ItemFixpoint}, the implementation the engine actually calls: the
 * surviving vocabulary and the round count must agree exactly, or this exits
 * non-zero. A replay that agreed with nothing would be one more unverified
 * source.</p>
 *
 * <p>Counts do not depend on the machine, so this belongs in results-invariant/.</p>
 *
 * Usage: java FixpointRounds &lt;dataset&gt; &lt;k1,k2,...&gt; [out.csv]
 */
public class FixpointRounds {
    public static void main(String[] a) throws Exception {
        String in = a[0];
        String[] ks = a[1].split(",");
        StringBuilder csv = new StringBuilder(
                "dataset,k,theta,round,items_removed,items_alive_after\n");
        boolean allOk = true;

        for (String kt : ks) {
            int k = Integer.parseInt(kt.trim());
            Dataset.DatasetResult res = new Dataset(false, 2000).loadDataset(in, 0);
            List<SequenceData> db = res.sequenceDatabase;
            int theta = SurThreshold.compute(db, res.sequenceUtilities,
                                             AlgoTKCM.itemUtilities(db), k);

            Set<Integer> alive = new TreeSet<>();
            for (SequenceData s : db) for (int it : s.itemNames) alive.add(it);
            int initial = alive.size();

            List<Integer> perRound = new ArrayList<>();
            // Mirrors ItemFixpoint.compute exactly, including the two details
            // that change the answer: a q-sequence contributing zero over the
            // surviving items is skipped rather than counted as zero, and the
            // loop stops on the round where the surviving set stops shrinking,
            // so that confirming round is counted.
            while (true) {
                Map<Integer, Long> swu = new HashMap<>();
                for (SequenceData seq : db) {
                    long uF = 0;
                    int[][] util = seq.matrixItemUtility;
                    int nbCols = util[0].length;
                    for (int r = 0; r < seq.itemNames.length; r++) {
                        if (!alive.contains(seq.itemNames[r])) continue;
                        int[] row = util[r];
                        for (int c = 0; c < nbCols; c++) uF += row[c];
                    }
                    if (uF == 0) continue;
                    for (int r = 0; r < seq.itemNames.length; r++) {
                        int item = seq.itemNames[r];
                        if (!alive.contains(item)) continue;
                        swu.merge(item, uF, Long::sum);
                    }
                }
                Set<Integer> next = new HashSet<>();
                for (Map.Entry<Integer, Long> e : swu.entrySet())
                    if (e.getValue() >= theta) next.add(e.getKey());
                int dropped = alive.size() - next.size();
                boolean stable = next.size() == alive.size();
                alive = new TreeSet<>(next);
                perRound.add(dropped);
                csv.append(String.format("%s,%d,%d,%d,%d,%d%n",
                        new File(in).getName().replace(".txt", ""), k, theta,
                        perRound.size(), dropped, alive.size()));
                if (stable || alive.isEmpty()) break;
            }

            // The check. ItemFixpoint is what the engine calls; if this replay
            // has drifted from it, every number above is worthless.
            ItemFixpoint.Result fp = ItemFixpoint.compute(db, theta);
            boolean ok = fp.items.size() == alive.size() && fp.rounds == perRound.size();
            allOk &= ok;
            int removed = initial - alive.size();
            System.out.printf("%-16s k=%-5d theta=%-8d %2d rounds, %,d of %,d items removed"
                    + "   replay vs ItemFixpoint: %s%n",
                    new File(in).getName(), k, theta, perRound.size(), removed, initial,
                    ok ? "agree" : "DISAGREE (fixpoint says " + fp.items.size()
                         + " alive in " + fp.rounds + " rounds)");
            System.out.printf("    per round: %s%n", perRound);
        }

        if (a.length > 2) {
            Path out = Paths.get(a[2]);
            Files.createDirectories(out.getParent());
            Files.writeString(out, csv.toString());
            System.out.println("wrote " + out);
        }
        System.exit(allOk ? 0 : 1);
    }
}
