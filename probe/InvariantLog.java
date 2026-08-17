import java.io.*; import java.nio.file.*; import java.security.MessageDigest;
import java.util.*;
/**
 * Appends probe results to results-invariant/, kept apart from results/.
 *
 * <p>Everything written here is a counter or a hash, and those do not depend on
 * the machine. The separation is structural rather than a convention: this
 * schema has <b>no wall-clock and no memory column</b>, so a timing cannot be
 * read out of these files even by mistake, and the evaluation matrix accepts
 * them only for claims stated in counters.</p>
 */
public final class InvariantLog {
    private InvariantLog() {}

    private static String sha256(Path p) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Files.readAllBytes(p));
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private static String exec(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String s = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return s;
        } catch (Exception e) { return ""; }
    }

    /** One row. `counters` is a semicolon-separated key=value list. */
    public static void row(String runId, String dataset, int k, String algo,
                           String status, long patterns, int minUtility,
                           String outputSha, String counters) {
        try {
            Path dir = Paths.get("results-invariant");
            Files.createDirectories(dir);
            Path f = dir.resolve("invariant-" + runId + ".csv");
            if (!Files.exists(f)) {
                Files.writeString(f,
                    "# counters and hashes only -- no wall-clock, no memory, "
                  + "so these rows can never be read as timings\n"
                  + "run_id,timestamp,host,jvm,git_commit,git_dirty,dataset,ds_sha256,"
                  + "algo,k,status,patterns,min_utility,output_sha256,counters\n",
                    StandardOpenOption.CREATE_NEW);
            }
            String dirty = exec("git", "status", "--porcelain", "--", "src", "bench").isEmpty()
                         ? "false" : "true";
            String row = String.join(",",
                runId,
                new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()),
                exec("hostname"),
                "\"" + System.getProperty("java.vendor") + " " + System.getProperty("java.version") + "\"",
                exec("git", "rev-parse", "--short", "HEAD"),
                dirty,
                "datasets/" + dataset + ".txt",
                sha256(Paths.get("datasets/" + dataset + ".txt")),
                algo, String.valueOf(k), status, String.valueOf(patterns),
                String.valueOf(minUtility), outputSha, "\"" + counters + "\"");
            Files.writeString(f, row + "\n", StandardOpenOption.APPEND);
        } catch (Exception e) {
            System.err.println("  ! could not append to results-invariant: " + e);
        }
    }
}
