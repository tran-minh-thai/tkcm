/**
 * High-precision timer class for measuring code execution time.
 * Uses System.nanoTime() for nanosecond precision timing.
 *
 * Usage examples:
 *
 * // Simple timing
 * Timer timer = new Timer();
 * timer.start();
 * // ... code to measure ...
 * timer.stop();
 * System.out.println("Elapsed: " + timer.formatElapsed());
 *
 * // Multiple measurements
 * Timer timer = new Timer();
 * timer.start();
 * // ... section 1 ...
 * timer.lap("Section 1");
 * // ... section 2 ...
 * timer.lap("Section 2");
 * timer.stop();
 * timer.printSummary();
 *
 * // Static convenience methods
 * Timer.measure(() -> {
 *     // code to measure
 * });
 */
import java.util.ArrayList;
import java.util.List;

public class Timer {

    private long startTime;
    private long stopTime;
    private boolean running;
    private List<LapRecord> laps;

    // Time unit constants (in nanoseconds)
    public static final long NANOS_PER_MICRO = 1000L;
    public static final long NANOS_PER_MILLI = 1000000L;
    public static final long NANOS_PER_SECOND = 1000000000L;
    public static final long NANOS_PER_MINUTE = 60000000000L;
    public static final long NANOS_PER_HOUR = 3600000000000L;

    /**
     * Time unit enumeration
     */
    public enum TimeUnit { NANOSECONDS, MICROSECONDS, MILLISECONDS, SECONDS, MINUTES, HOURS }

    /**
     * Record for storing lap/split times
     */
    private static class LapRecord {
        String label;
        long timestamp;
        long elapsed;

        LapRecord(String label, long timestamp, long elapsed) {
            this.label = label;
            this.timestamp = timestamp;
            this.elapsed = elapsed;
        }
    }

    /**
     * Constructor - creates a new timer in stopped state
     */
    public Timer() {
        this.running = false;
        this.laps = new ArrayList<>();
        reset();
    }

    /**
     * Start or restart the timer
     * @return this Timer instance for method chaining
     */
    public Timer start() {
        startTime = System.nanoTime();
        running = true;
        laps.clear();
        return this;
    }

    /**
     * Stop the timer
     * @return elapsed time in nanoseconds
     */
    public long stop() {
        if (running) {
            stopTime = System.nanoTime();
            running = false;
        }
        return getElapsed();
    }

    /**
     * Reset the timer to initial state
     */
    public void reset() {
        startTime = 0;
        stopTime = 0;
        running = false;
        laps.clear();
    }

    /**
     * Record a lap/split time with a label
     * @param label Description of the lap
     * @return elapsed time for this lap in nanoseconds
     */
    public long lap(String label) {
        if (!running) {
            throw new IllegalStateException("Timer is not running. Call start() first.");
        }

        long currentTime = System.nanoTime();
        long previousTime = laps.isEmpty() ? startTime : laps.get(laps.size() - 1).timestamp;
        long lapElapsed = currentTime - previousTime;

        laps.add(new LapRecord(label, currentTime, lapElapsed));
        return lapElapsed;
    }

    /**
     * Record a lap/split time without a label
     * @return elapsed time for this lap in nanoseconds
     */
    public long lap() {
        return lap("Lap " + (laps.size() + 1));
    }

    /**
     * Get elapsed time in nanoseconds
     * @return elapsed time in nanoseconds
     */
    public long getElapsed() {
        if (running) {
            return System.nanoTime() - startTime;
        } else {
            return stopTime - startTime;
        }
    }

    /**
     * Get elapsed time in specified unit
     * @param unit Time unit
     * @return elapsed time in specified unit
     */
    public double getElapsed(TimeUnit unit) {
        return convertNanos(getElapsed(), unit);
    }

    /**
     * Check if timer is currently running
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get number of laps recorded
     * @return number of laps
     */
    public int getLapCount() {
        return laps.size();
    }

    /**
     * Convert nanoseconds to specified time unit
     * @param nanos Time in nanoseconds
     * @param unit Target time unit
     * @return Converted time value
     */
    public static double convertNanos(long nanos, TimeUnit unit) {
        switch (unit) {
            case NANOSECONDS:   return nanos;
            case MICROSECONDS:  return nanos / (double) NANOS_PER_MICRO;
            case MILLISECONDS:  return nanos / (double) NANOS_PER_MILLI;
            case SECONDS:       return nanos / (double) NANOS_PER_SECOND;
            case MINUTES:       return nanos / (double) NANOS_PER_MINUTE;
            case HOURS:         return nanos / (double) NANOS_PER_HOUR;
            default:            return nanos;
        }
    }

    /**
     * Format nanoseconds as human-readable string with automatic unit selection
     * @param nanos Time in nanoseconds
     * @return Formatted string (e.g., "1.50 ms", "2.30 s")
     */
    public static String formatNanos(long nanos) {
        if (nanos < NANOS_PER_MICRO) {
            return nanos + " ns";
        } else if (nanos < NANOS_PER_MILLI) {
            return String.format("%.2f μs", nanos / (double) NANOS_PER_MICRO);
        } else if (nanos < NANOS_PER_SECOND) {
            return String.format("%.2f ms", nanos / (double) NANOS_PER_MILLI);
        } else if (nanos < NANOS_PER_MINUTE) {
            return String.format("%.2f s", nanos / (double) NANOS_PER_SECOND);
        } else if (nanos < NANOS_PER_HOUR) {
            return String.format("%.2f min", nanos / (double) NANOS_PER_MINUTE);
        } else {
            return String.format("%.2f h", nanos / (double) NANOS_PER_HOUR);
        }
    }

    /**
     * Format time with specified unit
     * @param value Time value
     * @param unit Time unit
     * @return Formatted string
     */
    public static String formatTime(double value, TimeUnit unit) {
        String unitStr;
        switch (unit) {
            case NANOSECONDS:   unitStr = "ns"; break;
            case MICROSECONDS:  unitStr = "μs"; break;
            case MILLISECONDS:  unitStr = "ms"; break;
            case SECONDS:       unitStr = "s"; break;
            case MINUTES:       unitStr = "min"; break;
            case HOURS:         unitStr = "h"; break;
            default:            unitStr = "ns"; break;
        }

        if (unit == TimeUnit.NANOSECONDS) {
            return String.format("%.0f %s", value, unitStr);
        } else {
            return String.format("%.2f %s", value, unitStr);
        }
    }

    /**
     * Format elapsed time with automatic unit selection
     * @return Formatted elapsed time string
     */
    public String formatElapsed() {
        return formatNanos(getElapsed());
    }

    /**
     * Format elapsed time with specified unit
     * @param unit Time unit
     * @return Formatted elapsed time string
     */
    public String formatElapsed(TimeUnit unit) {
        return formatTime(getElapsed(unit), unit);
    }

    /**
     * Print timing summary including all laps
     */
    public void printSummary() {
        System.out.println("=== Timer Summary ===");
        System.out.println("Total elapsed: " + formatElapsed());

        if (!laps.isEmpty()) {
            System.out.println("\nLap times:");
            for (int i = 0; i < laps.size(); i++) {
                LapRecord lap = laps.get(i);
                System.out.printf("  %2d. %-20s : %s\n",
                    i + 1, lap.label, formatNanos(lap.elapsed));
            }
        }
        System.out.println("====================");
    }

    /**
     * Print timing summary with colors
     */
    public void printColoredSummary() {
        System.out.println(ANSI.BOLD + "=== Timer Summary ===" + ANSI.RESET);
        System.out.println("Total elapsed: " + ANSI.GREEN + formatElapsed() + ANSI.RESET);

        if (!laps.isEmpty()) {
            System.out.println("\n" + ANSI.BOLD + "Lap times:" + ANSI.RESET);
            for (int i = 0; i < laps.size(); i++) {
                LapRecord lap = laps.get(i);
                System.out.printf("  %s%2d.%s %-20s : %s%s%s\n",
                    ANSI.CYAN, i + 1, ANSI.RESET,
                    lap.label,
                    ANSI.YELLOW, formatNanos(lap.elapsed), ANSI.RESET);
            }
        }
        System.out.println(ANSI.BOLD + "====================" + ANSI.RESET);
    }

    /**
     * Get detailed statistics about lap times
     * @return Statistics string
     */
    public String getLapStatistics() {
        if (laps.isEmpty()) {
            return "No laps recorded.";
        }

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long total = 0;

        for (LapRecord lap : laps) {
            if (lap.elapsed < min) min = lap.elapsed;
            if (lap.elapsed > max) max = lap.elapsed;
            total += lap.elapsed;
        }

        double avg = total / (double) laps.size();

        StringBuilder sb = new StringBuilder();
        sb.append("Lap Statistics:\n");
        sb.append("  Count   : ").append(laps.size()).append("\n");
        sb.append("  Min     : ").append(formatNanos(min)).append("\n");
        sb.append("  Max     : ").append(formatNanos(max)).append("\n");
        sb.append("  Average : ").append(formatNanos((long) avg)).append("\n");
        sb.append("  Total   : ").append(formatNanos(total));

        return sb.toString();
    }

    // ========== Static Utility Methods ==========

    /**
     * Measure execution time of a code block
     * @param code Code to measure
     * @return Elapsed time in nanoseconds
     */
    public static long measure(Runnable code) {
        long start = System.nanoTime();
        code.run();
        return System.nanoTime() - start;
    }

    /**
     * Measure and print execution time of a code block
     * @param label Description of the code being measured
     * @param code Code to measure
     * @return Elapsed time in nanoseconds
     */
    public static long measureAndPrint(String label, Runnable code) {
        long elapsed = measure(code);
        System.out.println(label + ": " + formatNanos(elapsed));
        return elapsed;
    }

    /**
     * Measure and print execution time with color
     * @param label Description of the code being measured
     * @param code Code to measure
     * @return Elapsed time in nanoseconds
     */
    public static long measureAndPrintColored(String label, Runnable code) {
        long elapsed = measure(code);
        System.out.println(label + ": " + ANSI.GREEN + formatNanos(elapsed) + ANSI.RESET);
        return elapsed;
    }

    /**
     * Benchmark a code block multiple times and return statistics
     * @param iterations Number of iterations
     * @param code Code to benchmark
     * @return Array of [min, max, average] in nanoseconds
     */
    public static long[] benchmark(int iterations, Runnable code) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long total = 0;

        // Warm-up run
        code.run();

        for (int i = 0; i < iterations; i++) {
            long elapsed = measure(code);
            if (elapsed < min) min = elapsed;
            if (elapsed > max) max = elapsed;
            total += elapsed;
        }

        long avg = total / iterations;
        return new long[]{min, max, avg};
    }

    /**
     * Benchmark and print statistics
     * @param label Description of the benchmark
     * @param iterations Number of iterations
     * @param code Code to benchmark
     */
    public static void benchmarkAndPrint(String label, int iterations, Runnable code) {
        System.out.println("Benchmarking: " + label + " (" + iterations + " iterations)");
        long[] stats = benchmark(iterations, code);
        System.out.println("  Min    : " + formatNanos(stats[0]));
        System.out.println("  Max    : " + formatNanos(stats[1]));
        System.out.println("  Average: " + formatNanos(stats[2]));
    }

    /**
     * Benchmark and print colored statistics
     * @param label Description of the benchmark
     * @param iterations Number of iterations
     * @param code Code to benchmark
     */
    public static void benchmarkAndPrintColored(String label, int iterations, Runnable code) {
        System.out.println(ANSI.BOLD + "Benchmarking: " + label + " (" + iterations + " iterations)" + ANSI.RESET);
        long[] stats = benchmark(iterations, code);
        System.out.println("  Min    : " + ANSI.GREEN + formatNanos(stats[0]) + ANSI.RESET);
        System.out.println("  Max    : " + ANSI.RED + formatNanos(stats[1]) + ANSI.RESET);
        System.out.println("  Average: " + ANSI.YELLOW + formatNanos(stats[2]) + ANSI.RESET);
    }
}
