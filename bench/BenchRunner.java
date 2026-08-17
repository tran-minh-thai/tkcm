import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Self-describing benchmark pipeline (editorial rules 2.6.5 / 2.6.8).
 *
 * Parent mode:  java BenchRunner [--datasets a,b] [--k 10,20] [--algos tkus,etk]
 *                                [--xmx 8g] [--timeout 1800] [--out results]
 *   For each (dataset, k, algo) configuration, spawns a FRESH child JVM
 *   (isolated JIT/GC state; survives OOM and hangs), collects one CSV row per
 *   measured repetition, and appends to results/results-<run_id>.csv. Every row
 *   carries the full configuration and environment — nothing lives in file
 *   names (rule 2.6.5). run_id = timestamp + git commit (rule 2.6.8).
 *
 * Child mode:   java -Xmx.. BenchRunner --child <dsPath> <k> <algo>
 *   algo = tkus | etk | eng-w#s#c#m#f# | uspan-oracle | load | sur.
 *     load          — Dataset.loadDataset only, the shared fixed cost.
 *     sur           — load + the SUR threshold, then stop. Seconds on any
 *                     dataset, so a configuration that never finishes still
 *                     gets a threshold on record (rules 2.2.6 / 2.2.7).
 *     uspan-oracle  — the different-family baseline required by rule 2.3.13:
 *                     threshold-based USpan handed the exact minUtility that
 *                     top-k converges to. The threshold is derived OUTSIDE the
 *                     timed region (that is what "oracle" means) and is
 *                     computed, never typed into a script (rule 2.6.8).
 *                     NOT a lower bound on top-k cost: USpan's own pruning is
 *                     weaker than TKUS's, so knowing the threshold does not
 *                     make it cheapest. Measured sign k=50: oracle 9.35 s vs
 *                     tkus 8.49 s. It is a baseline and a cross-family
 *                     correctness oracle, nothing stronger.
 *   Warm-up: 1 unmeasured run, +1 more if the first took < 10 s.
 *   Repetitions tiered by wall time (rule 2.4.1): <1s:15, <10s:10, <120s:5,
 *   else 3. The tier is taken from the SHORTER of the warm-up and the first
 *   measured run, so a cold-JIT warm-up cannot leave a cell under-replicated
 *   relative to the mean it eventually reports. One "DATA:" line per
 *   repetition.
 *
 * Correctness signal: output_sha256 is the SHA-256 of the sorted pattern lines;
 * equal hashes across algorithms at the same (dataset,k) certify identical
 * result sets, machine-independently (rule 2.2.5).
 */
public class BenchRunner {

    // ---- repetition tiers (rule 2.4.1) ----
    static int tier(double firstWallMs) {
        if (firstWallMs < 1_000)   return 15;
        if (firstWallMs < 10_000)  return 10;
        if (firstWallMs < 120_000) return 5;
        return 3;
    }

    /**
     * The machine whose runs may be written to results/. Timings depend on the
     * machine, so mixing hosts there produces a table that cannot be compared
     * row to row. Counter-only quantities do not, which is what results-probe/
     * and results-invariant/ are for.
     */
    static final String MEASURING_HOST = "Admin-PC";

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--child")) { child(args); return; }
        parent(args);
    }

    // =====================================================================
    // Child: runs one (dataset, k, algo) configuration, one JVM.
    // =====================================================================
    static void child(String[] a) throws Exception {
        String ds = a[1]; int k = Integer.parseInt(a[2]); String algo = a[3];
        int maxReps    = a.length > 4 ? Integer.parseInt(a[4]) : 0;   // 0 = tier decides
        boolean warmup = a.length <= 5 || !a[5].equals("none");
        int perRunSec  = a.length > 6 ? Integer.parseInt(a[6]) : 0;   // 0 = no per-run limit
        deadlineSeconds = perRunSec;

        // Warm-up (rule 2.4.4): 1 unmeasured run; +1 if the first took < 10 s.
        // Scouting mode (--warmup none): the first MEASURED run is the tier
        // estimator; warmups=0 is recorded so such rows are self-identifying.
        //
        // The tier is decided from the SHORTER of the warm-up and the first
        // measured run. A cold warm-up runs slower than the steady state, and
        // reading the tier off it alone put five sign cells into the 5-rep
        // tier although every measured run of theirs landed under 10 s — the
        // published mean then sat below the replication its own magnitude
        // requires (rule 2.4.1). tier() is monotone decreasing in time, so
        // taking the max of the two tiers takes the stricter requirement.
        int warmups = 0, reps;
        Rep first = null;
        // The tier is read from the SHORTER of the warm-up and the first measured
        // run, so the warm-up's time decides the requirement as often as the
        // measured one does. Recorded per row: without it, a cell sitting near a
        // tier boundary cannot be shown compliant from the artifact alone, and
        // two such cells could not be decided at all.
        try {
            if (warmup) {
                Rep w1 = timedRun(ds, k, algo, 0, 0, 0); warmups = 1; warmupMs = w1.wallMs;
                if (w1.wallMs < 10_000) {
                    Rep w2 = timedRun(ds, k, algo, 0, 0, 1); warmups = 2;
                    warmupMs = Math.min(warmupMs, w2.wallMs);
                }
                // reps is not known yet; if this run overruns, exactly one
                // attempt was made, so the deadline row must say reps=1.
                first = timedRun(ds, k, algo, 1, 1, warmups);
                reps = Math.max(tier(w1.wallMs), tier(first.wallMs));
            } else {
                first = timedRun(ds, k, algo, 1, 1, 0);
                reps = tier(first.wallMs);
            }
        } catch (OutOfMemoryError oom) {
            System.out.println("DATA:rep=1;reps=1;warmups=" + warmups + ";warmup_ms=" + (warmupMs < 0 ? "" : String.format("%.1f", warmupMs))
                    + ";status=OOM;wall_ms=;cpu_ms=;peak_heap_mb=;algo_peak_mb="
                    + ";patterns=;min_utility=;output_sha256=;counters=");
            System.out.flush();
            System.exit(3);
            return;
        }
        if (maxReps > 0 && reps > maxReps) reps = maxReps;

        // The tier must match the mean this cell will REPORT, and no single run
        // predicts that reliably. Taking the stricter of warm-up and first run
        // (above) fixed most of it, but sign k=20 eng-w1s1c1m0f0 still came back
        // with 5 repetitions for a mean of 9.33 s, because that cell's first
        // measured run happened to land just over 10 s while the mean landed
        // just under. So the tier is re-evaluated against the running mean after
        // every repetition and the count is raised when it has to be. It never
        // falls, so this terminates at 15 at worst.
        //
        // Rows are therefore buffered and printed at the end: every row of a
        // cell must carry the SAME final `reps`, and that value is not known
        // until the last measurement. OOM and TIMEOUT keep their own immediate
        // paths — they halt the JVM, so nothing would be left to flush.
        List<String> pending = new ArrayList<>();
        List<Double> walls = new ArrayList<>();
        for (int r = 1; r <= reps; r++) {
            Rep m;
            try {
                m = (r == 1 && first != null) ? first : timedRun(ds, k, algo, r, reps, warmups);
            } catch (OutOfMemoryError oom) {
                for (String line : pending) System.out.println(line.replace("${REPS}", String.valueOf(reps)));
                System.out.println("DATA:rep=" + r + ";reps=" + reps + ";warmups=" + warmups + ";warmup_ms=" + (warmupMs < 0 ? "" : String.format("%.1f", warmupMs))
                        + ";status=OOM;wall_ms=;cpu_ms=;peak_heap_mb=;algo_peak_mb="
                        + ";patterns=;min_utility=;output_sha256=;counters=");
                System.out.flush();
                System.exit(3); // heap state unreliable after OOM — stop this config
                return;
            }
            walls.add(m.wallMs);
            pending.add("DATA:rep=" + r + ";reps=${REPS};warmups=" + warmups + ";warmup_ms=" + (warmupMs < 0 ? "" : String.format("%.1f", warmupMs))
                    + ";status=OK"
                    + ";wall_ms=" + String.format("%.1f", m.wallMs)
                    + ";cpu_ms=" + String.format("%.1f", m.cpuMs)
                    + ";peak_heap_mb=" + String.format("%.1f", m.peakHeapMb)
                    + ";algo_peak_mb=" + String.format("%.1f", m.algoPeakMb)
                    + ";patterns=" + m.patterns
                    + ";min_utility=" + m.minUtility
                    + ";output_sha256=" + m.sha256
                    + ";counters=" + m.counters);

            double sum = 0; for (double w : walls) sum += w;
            int wanted = tier(sum / walls.size());
            if (maxReps > 0 && wanted > maxReps) wanted = maxReps;
            if (wanted > reps) reps = wanted;
        }
        System.out.println("META:warmups=" + warmups + ";reps=" + reps);
        for (String line : pending) System.out.println(line.replace("${REPS}", String.valueOf(reps)));
        System.out.flush();
    }

    static class Rep {
        double wallMs, cpuMs, peakHeapMb, algoPeakMb;
        int patterns, minUtility;
        String sha256 = "", counters = "";
    }

    /**
     * Wall-clock ceiling for ONE run, in seconds; 0 disables it. The limit is
     * per run rather than per configuration so that a reported "&gt; N min"
     * means a single execution exceeded N minutes — which is how a reader
     * interprets it — instead of meaning that warm-up plus every repetition
     * together did.
     */
    static int deadlineSeconds = 0;

    /**
     * Arms a daemon timer that reports a TIMEOUT row and halts the JVM if the
     * current run overruns. {@code halt} rather than {@code exit} because
     * shutdown hooks could block on a mining structure that is still growing.
     */
    /** Warm-up time of the configuration being measured, -1 before one is taken.
     *  Static because the deadline thread reports a row from outside the method
     *  that owns the measurement. */
    static volatile double warmupMs = -1;

    static Thread armDeadline(int rep, int reps, int warmups) {
        if (deadlineSeconds <= 0) return null;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(deadlineSeconds * 1000L);
                System.out.println("DATA:rep=" + rep + ";reps=" + reps + ";warmups=" + warmups + ";warmup_ms=" + (warmupMs < 0 ? "" : String.format("%.1f", warmupMs))
                        + ";status=TIMEOUT;wall_ms=;cpu_ms=;peak_heap_mb=;algo_peak_mb="
                        + ";patterns=;min_utility=;output_sha256=;counters=");
                System.out.flush();
                Runtime.getRuntime().halt(124);
            } catch (InterruptedException ignored) {
                // run finished within the limit
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** One run under the per-run deadline (if any). */
    static Rep timedRun(String ds, int k, String algo, int rep, int reps, int warmups)
            throws Exception {
        Thread guard = armDeadline(rep, reps, warmups);
        try {
            return runOnce(ds, k, algo);
        } finally {
            if (guard != null) guard.interrupt();
        }
    }

    static Rep runOnce(String ds, int k, String algo) throws Exception {
        // The oracle baseline is *given* the threshold, so deriving it is not
        // part of what is measured: it happens here, before the clock starts.
        // Any exact configuration yields the same minUtility (verified over
        // every completed cell), so the cheapest one is used.
        int oracleTheta = 0;
        if (algo.equals("uspan-oracle")) {
            File probeOut = File.createTempFile("oracle-probe", ".txt");
            probeOut.deleteOnExit();
            AlgoTKCM probe = new AlgoTKCM(true, true, true, true, true);
            probe.runAlgorithm(ds, probeOut.getPath(), k);
            oracleTheta = probe.minUtility;
            probeOut.delete();
        }

        System.gc();
        Thread.sleep(50);
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans())
            if (p.getType() == MemoryType.HEAP) p.resetPeakUsage();

        com.sun.management.OperatingSystemMXBean osb =
            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        File out = File.createTempFile("bench-" + algo, ".txt");
        out.deleteOnExit();

        long cpu0 = osb.getProcessCpuTime(), t0 = System.nanoTime();
        Rep r = new Rep();
        if (algo.equals("tkus")) {
            AlgoTKUS x = new AlgoTKUS();
            // and this project corrected. It emits a DIFFERENT pattern set by
            // construction, so it is excluded from the cross-configuration
            // agreement check below, and its wall clock is not a speed
            // comparison: it is faster partly because it explores less, and it
            // explores less because it loses patterns.
            x.runAlgorithm(ds, out.getPath(), k);
            r.patterns = x.patternCount; r.minUtility = x.minUtility; r.algoPeakMb = x.memoryUsed;
            r.counters = "cands0=" + x.candidatesDepth0 + ";candsI=" + x.candidatesI
                    + ";candsS=" + x.candidatesS + ";prunedEUI0=" + x.prunedByEUI0
                    + ";prunedEUI_I=" + x.prunedByEUI_I + ";prunedEUI_S=" + x.prunedByEUI_S
                    + ";prunedTDE=" + x.prunedByTDE;
        } else if (algo.startsWith("eng-")) {
            // eng-w{0|1}s{0|1}c{0|1}[m{0|1}][f{0|1}][r{0|1}]: one flag
            // per factor. Every flag must be threaded here; a missing one
            // silently falls back to the constructor default and the run
            // measures the wrong configuration. The vocab* counters make that
            // visible in the CSV.
            String c = algo.substring(4);
            AlgoTKCM x = new AlgoTKCM(c.contains("w1"), c.contains("s1"),
                                        c.contains("c1"), c.contains("m1"),
                                        c.contains("f1"), c.contains("r1"));
            x.runAlgorithm(ds, out.getPath(), k);
            r.patterns = x.patternCount; r.minUtility = x.minUtility; r.algoPeakMb = x.memoryUsed;
            r.counters = x.countersString();
        } else if (algo.equals("uspan-oracle")) {
            AlgoUSpan x = new AlgoUSpan();
            x.runAlgorithm(ds, out.getPath(), oracleTheta);
            r.patterns = x.patternCount; r.minUtility = oracleTheta;
            r.algoPeakMb = MemoryLogger.getInstance().getMaxMemory();
            // patterns is the count at or above the threshold, which is >= k
            // when several patterns tie on the k-th utility. The identity
            // "top k of this output == our output" is what tab_verification
            // checks; it is not implied by the counts alone.
            r.counters = "oracleTheta=" + oracleTheta;
        } else if (algo.equals("sur")) {
            Dataset.DatasetResult res = new Dataset(false, 2000).loadDataset(ds, 0);
            int theta = SurThreshold.compute(res.sequenceDatabase, res.sequenceUtilities,
                    AlgoTKCM.itemUtilities(res.sequenceDatabase), k);
            r.patterns = res.sequenceDatabase.size(); r.minUtility = theta;
            r.counters = "surTheta=" + theta;
        } else if (algo.equals("load")) {
            Dataset.DatasetResult res = new Dataset(false, 2000).loadDataset(ds, 0);
            r.patterns = res.sequenceDatabase.size(); r.minUtility = 0;
        } else {
            throw new IllegalArgumentException("unknown algo " + algo);
        }
        r.wallMs = (System.nanoTime() - t0) / 1e6;
        r.cpuMs  = (osb.getProcessCpuTime() - cpu0) / 1e6;

        long peak = 0;
        for (MemoryPoolMXBean p : ManagementFactory.getMemoryPoolMXBeans())
            if (p.getType() == MemoryType.HEAP) peak += p.getPeakUsage().getUsed();
        r.peakHeapMb = peak / (1024.0 * 1024.0);

        // "load" and "sur" produce no patterns, so they carry no hash and stay
        // out of the cross-configuration agreement check. "uspan-oracle" does
        // carry one: run at exactly the threshold top-k converges to, it emits
        // the same set — all patterns with utility >= minUtility — so its hash
        // must equal every top-k configuration's. That equality is the only
        // correctness check in the suite that crosses algorithm families.
        if (!algo.equals("load") && !algo.equals("sur"))
            r.sha256 = sha256SortedLines(out.getPath());
        out.delete();
        return r;
    }

    static String sha256SortedLines(String path) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(path));
        lines.sort(String::compareTo);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String l : lines) { md.update(l.getBytes(StandardCharsets.UTF_8)); md.update((byte) '\n'); }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // =====================================================================
    // Parent: orchestration + self-describing CSV.
    // =====================================================================
    static void parent(String[] args) throws Exception {
        Map<String, String> opt = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) opt.put(args[i], args[i + 1]);
        String[] datasets = opt.getOrDefault("--datasets", "uspan,HUSRM").split(",");
        String[] ks       = opt.getOrDefault("--k", "10").split(",");
        String[] algos    = opt.getOrDefault("--algos", "load,tkus,eng-w1s1c1m1f1").split(",");
        String   xmx      = opt.getOrDefault("--xmx", "8g");
        int timeoutSec    = Integer.parseInt(opt.getOrDefault("--timeout", "1800"));
        String outDir     = opt.getOrDefault("--out", "results");
        String maxReps    = opt.getOrDefault("--maxreps", "0");      // 0 = tier decides
        String warmupMode = opt.getOrDefault("--warmup", "auto");    // auto | none (scouting)

        String ts     = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String commit = exec("git", "rev-parse", "--short", "HEAD").trim();
        boolean dirty = !exec("git", "status", "--porcelain", "--",
                "src", "bench", "test", "datasets", "build.sh", "test.sh").trim().isEmpty();
        String runId  = ts + "-" + (commit.isEmpty() ? "nogit" : commit) + (dirty ? "-dirty" : "");

        // REFUSE rather than stamp-and-continue.
        //
        // A dirty tree means the sources being measured are not the ones the
        // commit names, so every row produced is unattributable and
        // make_tables.py will discard it. Stamping "-dirty" and running anyway
        // turned an instant, obvious failure into an hour of wasted machine
        // time — three times in one day, because the guard lived in a wrapper
        // script (xps.sh) that could itself be stale or simply not used. The
        // check belongs in the program that writes the data, where nothing can
        // route around it.
        //
        // Scouting probes on a working tree are legitimate; they just have to
        // say so:  ALLOW_DIRTY=1 bash bench.sh ...
        if (dirty && !"1".equals(System.getenv("ALLOW_DIRTY"))) {
            System.err.println("REFUSING TO MEASURE: tracked sources differ from commit " + commit + ".");
            System.err.println();
            System.err.println(exec("git", "status", "--porcelain", "--",
                    "src", "bench", "test", "datasets", "build.sh", "test.sh").trim());
            System.err.println();
            System.err.println("Every row would be stamped '" + runId + "' and excluded from all");
            System.err.println("tables, so the run would cost machine time and produce nothing.");
            System.err.println();
            System.err.println("If a file sync is behind, wait for it to finish, then:");
            System.err.println("    git restore --source=HEAD --staged --worktree -- src bench test datasets build.sh test.sh");
            System.err.println();
            System.err.println("For a deliberate scouting run on modified sources:");
            System.err.println("    ALLOW_DIRTY=1 <your command>");
            System.exit(2);
        }

        String host  = exec("hostname").trim();
        String hw    = detectCpu();
        String os    = System.getProperty("os.name") + " " + System.getProperty("os.version")
                     + " " + System.getProperty("os.arch");
        String jvm   = System.getProperty("java.vendor") + " " + System.getProperty("java.version");
        int cores    = Runtime.getRuntime().availableProcessors();
        long memGb   = ((com.sun.management.OperatingSystemMXBean)
                ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize()
                / (1024L * 1024L * 1024L);

        // results/ is for the measuring machine only. Everything else writes to
        // results-probe/, which the analysis does not read.
        //
        // This gate exists because separating the two by convention has failed
        // before: a scouting run at a different heap ceiling landed in results/
        // and was read as measurement, and the mistake surfaced only when a
        // coverage matrix was rechecked. A default that writes to results/ from
        // any machine makes that mistake the easy path.
        //
        // An explicit --out is always honoured: the operator who names the
        // directory has said which kind of run this is.
        if (!opt.containsKey("--out") && !host.equalsIgnoreCase(MEASURING_HOST)) {
            System.err.println("refusing to write to results/ from host '" + host + "'.");
            System.err.println("results/ holds measurements from '" + MEASURING_HOST + "' only.");
            System.err.println("re-run with --out results-probe, or with an explicit --out.");
            System.exit(2);
        }

        new File(outDir).mkdirs();
        File csv = new File(outDir, "results-" + runId + ".csv");
        PrintWriter w = new PrintWriter(new FileWriter(csv));
        w.println("# run_id=" + runId + " (schema=3; one row per measured repetition; all config in columns)");
        w.println("run_id,timestamp,host,hardware,os,jvm,cores,mem_total_gb,xmx,timeout_s,git_commit,git_dirty,"
                + "dataset,ds_sha256,ds_sequences,ds_distinct_items,ds_total_itemsets,ds_total_utility,"
                + "algo,k,warmups,warmup_ms,rep,reps,status,wall_ms,cpu_ms,peak_heap_mb,algo_peak_mb,"
                + "patterns,min_utility,output_sha256,counters");

        Map<String, String[]> dsStats = new HashMap<>();
        String cp = System.getProperty("java.class.path");

        for (String d : datasets) {
            String dsPath = d.contains("/") ? d : "datasets/" + d + ".txt";
            String[] st = dsStats.computeIfAbsent(dsPath, BenchRunner::datasetStats);
            for (String kk : ks) {
                for (String algo : algos) {
                    System.out.println("== config: " + dsPath + " k=" + kk + " algo=" + algo + " ==");
                    // The child enforces the limit per run; the parent's own
                    // watchdog is a backstop for a child that wedges before it
                    // can report, so it allows for warm-up plus repetitions.
                    ProcessBuilder pb = new ProcessBuilder("java", "-Xmx" + xmx, "-cp", cp,
                            "BenchRunner", "--child", dsPath, kk, algo, maxReps, warmupMode,
                            String.valueOf(timeoutSec));
                    pb.redirectErrorStream(true);
                    Process proc = pb.start();
                    // Track watchdog kills explicitly: on Windows destroyForcibly()
                    // yields exit code 1 (not 137/143), so exit-code sniffing alone
                    // mislabels TIMEOUT as ERROR(1).
                    final boolean[] timedOut = {false};
                    final int backstopSec = timeoutSec * 18;   // warm-up + up to 15 reps + slack
                    Thread watchdog = new Thread(() -> {
                        try {
                            if (!proc.waitFor(backstopSec, TimeUnit.SECONDS)) {
                                timedOut[0] = true;
                                proc.destroyForcibly();
                            }
                        } catch (InterruptedException ignored) {}
                    });
                    watchdog.setDaemon(true); watchdog.start();

                    boolean sawData = false, oom = false, childReported = false;
                    BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.startsWith("DATA:")) {
                            sawData = true;
                            if (line.contains("status=OOM")) oom = true;
                            // The child reports its own terminal status when it
                            // hits the per-run deadline or runs out of heap; the
                            // parent must not then add a second row for the same
                            // configuration.
                            if (line.contains("status=OOM") || line.contains("status=TIMEOUT"))
                                childReported = true;
                            w.println(row(runId, ts, host, hw, os, jvm, cores, memGb, xmx, timeoutSec,
                                    commit, dirty, dsPath, st, algo, kk, line.substring(5)));
                            w.flush();
                        } else if (line.startsWith("META:")) {
                            System.out.println("   " + line);
                        } else if (line.contains("OutOfMemoryError")) {
                            oom = true;
                        }
                    }
                    int code = proc.waitFor();
                    if (!sawData || (code != 0 && !oom && !childReported)) {
                        String status = (timedOut[0] || code == 124 || code == 137 || code == 143)
                                      ? "TIMEOUT"
                                      : (oom ? "OOM" : "ERROR(" + code + ")");
                        w.println(row(runId, ts, host, hw, os, jvm, cores, memGb, xmx, timeoutSec,
                                commit, dirty, dsPath, st, algo, kk,
                                "rep=1;reps=1;warmups=0;status=" + status
                                + ";wall_ms=;cpu_ms=;peak_heap_mb=;algo_peak_mb="
                                + ";patterns=;min_utility=;output_sha256=;counters="));
                        w.flush();
                        System.out.println("   -> " + status);
                    } else if (oom && code != 0) {
                        System.out.println("   -> OOM (recorded)");
                    }
                }
            }
        }
        w.close();
        System.out.println("CSV: " + csv.getPath());
    }

    /** CPU model string, portably: macOS sysctl / Windows env / Linux cpuinfo. */
    static String detectCpu() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) return exec("sysctl", "-n", "machdep.cpu.brand_string").trim();
        if (osName.contains("win")) {
            String id = System.getenv("PROCESSOR_IDENTIFIER");
            String wmic = exec("wmic", "cpu", "get", "name").replace("Name", "").trim();
            return !wmic.isEmpty() ? wmic : (id != null ? id : "");
        }
        for (String l : exec("cat", "/proc/cpuinfo").split("\n"))
            if (l.startsWith("model name")) return l.substring(l.indexOf(':') + 1).trim();
        return "";
    }

    /** kv line ("a=1;b=2;counters=x=1;y=2") -> ordered CSV fields. */
    static String row(String runId, String ts, String host, String hw, String os, String jvm,
                      int cores, long memGb, String xmx, int timeoutSec, String commit, boolean dirty,
                      String dsPath, String[] st, String algo, String k, String kv) {
        Map<String, String> m = new HashMap<>();
        int cIdx = kv.indexOf(";counters=");
        String counters = "";
        if (cIdx >= 0) { counters = kv.substring(cIdx + 10); kv = kv.substring(0, cIdx); }
        for (String p : kv.split(";")) {
            int e = p.indexOf('=');
            if (e > 0) m.put(p.substring(0, e), p.substring(e + 1));
        }
        return String.join(",", runId, ts, host, q(hw), q(os), q(jvm), String.valueOf(cores),
                String.valueOf(memGb), xmx, String.valueOf(timeoutSec), commit, String.valueOf(dirty),
                dsPath, st[0], st[1], st[2], st[3], st[4],
                algo, k,
                // warmup_ms must appear here as well as in the header: this row is
                // built from a fixed join, so a column added to one and not the
                // other shifts every field after it. That happened -- the header
                // gained the column, the join did not, and two rows were written
                // whose `reps` read "OK" because it was holding `status`.
                m.getOrDefault("warmups", ""), m.getOrDefault("warmup_ms", ""),
                m.getOrDefault("rep", ""), m.getOrDefault("reps", ""),
                m.getOrDefault("status", ""), m.getOrDefault("wall_ms", ""), m.getOrDefault("cpu_ms", ""),
                m.getOrDefault("peak_heap_mb", ""), m.getOrDefault("algo_peak_mb", ""),
                m.getOrDefault("patterns", ""), m.getOrDefault("min_utility", ""),
                m.getOrDefault("output_sha256", ""), q(counters));
    }

    static String q(String s) { return "\"" + s.replace("\"", "'") + "\""; }

    /** {sha256, sequences, distinct items, total itemsets, total utility} */
    static String[] datasetStats(String path) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(path));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest(bytes)) hex.append(String.format("%02x", b));

            int seqs = 0; long itemsets = 0, totalUtil = 0;
            Set<Integer> items = new HashSet<>();
            for (String line : new String(bytes, StandardCharsets.UTF_8).split("\r?\n")) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') continue;
                String[] tok = line.trim().split("\\s+");
                int colon = tok[tok.length - 1].indexOf(':');
                if (colon < 0) continue;
                seqs++;
                totalUtil += Long.parseLong(tok[tok.length - 1].substring(colon + 1));
                // Count itemsets the way Dataset does: a "-1" closes the
                // itemset before it, and the one sitting immediately before -2
                // closes the last one rather than opening another. Bounding the
                // scan by the last item token gives that, and does not assume
                // how many tokens trail the sequence — the assumption that a
                // fixed offset made, and that the double space before SUtility
                // in every published file here broke. This function and the
                // loader must agree, or ds_total_itemsets drifts away from what
                // was actually mined.
                int lastItem = -1;
                for (int i = 0; i < tok.length; i++) {
                    if (tok[i].indexOf('[') > 0) lastItem = i;
                    else if (tok[i].equals("-2")) break;
                }
                // The FIRST itemset opens without a separator, exactly as
                // Dataset does by starting nbItemsets at 1; the loop below then
                // adds one per separator that lies before the last item. Miss
                // this and the count is short by one on every sequence, which
                // is what the first attempt at this fix did.
                if (lastItem >= 0) itemsets++;
                for (int i = 0; i <= lastItem; i++) {
                    if (tok[i].equals("-1")) itemsets++;
                    else if (!tok[i].isEmpty() && tok[i].charAt(0) != '-') {
                        int br = tok[i].indexOf('[');
                        if (br > 0) items.add(Integer.parseInt(tok[i].substring(0, br)));
                    }
                }
            }
            return new String[]{hex.toString(), String.valueOf(seqs),
                    String.valueOf(items.size()), String.valueOf(itemsets), String.valueOf(totalUtil)};
        } catch (Exception e) {
            return new String[]{"", "", "", "", ""};
        }
    }

    static String exec(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            p.waitFor(10, TimeUnit.SECONDS);
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}
