import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Utility class for writing high-utility sequential pattern results to a file.
 *
 * Supports two output formats:
 *   - Human-readable : {@code <(a)(b)(c)>:utility}  (itemsets separated by parentheses)
 *   - SPMF format    : {@code a b c -1 #UTIL: utility}
 *
 * Patterns are encoded in an {@code int[]} prefix buffer where {@code -1} acts as an
 * itemset separator (same convention used by USpan / TKUS internally).
 *
 * Usage:
 * <pre>
 *   OutputResult out = new OutputResult("output.txt", true);
 *   out.writePattern(prefix, prefixLength, utility);
 *   out.close();
 * </pre>
 */
public class OutputResult {

    /** The underlying buffered writer */
    private final BufferedWriter writer;

    /** When true, patterns are written in human-readable format; otherwise SPMF format */
    private final boolean humanReadable;

    /** Number of patterns written so far */
    private int patternCount = 0;

    /**
     * Open an output file for writing patterns.
     *
     * @param filePath      path to the output file (created or overwritten)
     * @param humanReadable {@code true} for {@code <(a)(b)>:u} format,
     *                      {@code false} for SPMF {@code a b -1 #UTIL: u} format
     * @throws IOException if the file cannot be opened
     */
    public OutputResult(String filePath, boolean humanReadable) throws IOException {
        this.writer       = new BufferedWriter(new FileWriter(filePath));
        this.humanReadable = humanReadable;
    }

    /**
     * Write a single pattern to the output file.
     *
     * @param prefix        buffer holding the pattern; {@code -1} separates itemsets
     * @param prefixLength  number of elements from {@code prefix} that form the pattern
     * @param utility       the utility value of the pattern
     * @throws IOException if an error occurs while writing
     */
    public void writePattern(int[] prefix, int prefixLength, int utility) throws IOException {
        writer.write(formatPattern(prefix, prefixLength, utility));
        writer.newLine();
        patternCount++;
    }

    /**
     * Format a pattern as a string without writing it.
     * Useful for logging or debug output.
     *
     * @param prefix        buffer holding the pattern; {@code -1} separates itemsets
     * @param prefixLength  number of elements from {@code prefix} that form the pattern
     * @param utility       the utility value of the pattern
     * @return the formatted string representation
     */
    public String formatPattern(int[] prefix, int prefixLength, int utility) {
        return formatStatic(prefix, prefixLength, utility, humanReadable);
    }

    /**
     * Flush and close the output file.
     *
     * @throws IOException if an error occurs while closing
     */
    public void close() throws IOException {
        writer.close();
    }

    /**
     * Static variant of {@link #formatPattern} — useful for debug/logging when no
     * {@code OutputResult} instance is available.
     *
     * @param prefix        buffer holding the pattern; {@code -1} separates itemsets
     * @param prefixLength  number of elements from {@code prefix} that form the pattern
     * @param utility       the utility value of the pattern
     * @param humanReadable {@code true} for human-readable format, {@code false} for SPMF
     * @return the formatted string representation
     */
    public static String formatStatic(int[] prefix, int prefixLength, int utility, boolean humanReadable) {
        StringBuilder sb = new StringBuilder();
        if (humanReadable) {
            sb.append('<').append('(');
            for (int i = 0; i < prefixLength; i++) {
                if (prefix[i] == -1) {
                    sb.append(")(");
                } else {
                    sb.append(prefix[i]);
                }
            }
            sb.append(")>:").append(utility);
        } else {
            for (int i = 0; i < prefixLength; i++) {
                sb.append(prefix[i]).append(' ');
            }
            sb.append("-1 #UTIL: ").append(utility);
        }
        return sb.toString();
    }

    /**
     * Return the number of patterns written so far.
     *
     * @return pattern count
     */
    public int getPatternCount() {
        return patternCount;
    }
}
