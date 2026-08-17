public class ANSI {

    //  References:
    //  - https://gist.github.com/fnky/458719343aabd01cfb17a3a4f7296797

    // Basic 16 ANSI colors (foreground)
    public final static String BLACK   = "\u001B[30m";
    public final static String RED     = "\u001B[31m";
    public final static String GREEN   = "\u001B[32m";
    public final static String YELLOW  = "\u001B[33m";
    public final static String BLUE    = "\u001B[34m";
    public final static String MAGENTA = "\u001B[35m";
    public final static String CYAN    = "\u001B[36m";
    public final static String WHITE   = "\u001B[37m";

    // Bright/Bold variants
    public final static String BRIGHT_BLACK   = "\u001B[90m";
    public final static String BRIGHT_RED     = "\u001B[91m";
    public final static String BRIGHT_GREEN   = "\u001B[92m";
    public final static String BRIGHT_YELLOW  = "\u001B[93m";
    public final static String BRIGHT_BLUE    = "\u001B[94m";
    public final static String BRIGHT_MAGENTA = "\u001B[95m";
    public final static String BRIGHT_CYAN    = "\u001B[96m";
    public final static String BRIGHT_WHITE   = "\u001B[97m";

    // Background colors (basic 16)
    public final static String BG_BLACK   = "\u001B[40m";
    public final static String BG_RED     = "\u001B[41m";
    public final static String BG_GREEN   = "\u001B[42m";
    public final static String BG_YELLOW  = "\u001B[43m";
    public final static String BG_BLUE    = "\u001B[44m";
    public final static String BG_MAGENTA = "\u001B[45m";
    public final static String BG_CYAN    = "\u001B[46m";
    public final static String BG_WHITE   = "\u001B[47m";

    // Bright background colors
    public final static String BG_BRIGHT_BLACK   = "\u001B[100m";
    public final static String BG_BRIGHT_RED     = "\u001B[101m";
    public final static String BG_BRIGHT_GREEN   = "\u001B[102m";
    public final static String BG_BRIGHT_YELLOW  = "\u001B[103m";
    public final static String BG_BRIGHT_BLUE    = "\u001B[104m";
    public final static String BG_BRIGHT_MAGENTA = "\u001B[105m";
    public final static String BG_BRIGHT_CYAN    = "\u001B[106m";
    public final static String BG_BRIGHT_WHITE   = "\u001B[107m";

    // 256-color palette (foreground) - Extended colors
    public final static String IVORY     = "\u001B[38;5;11m" ;
    public final static String ORANGE    = "\u001B[38;5;214m";
    public final static String PINK      = "\u001B[38;5;198m";
    public final static String GRAY      = "\u001B[38;5;251m";
    public final static String LIME      = "\u001B[38;5;190m";
    public final static String LAGOON    = "\u001B[38;5;153m";
    public final static String SAKURA    = "\u001B[38;5;5m"  ;
    public final static String DENIM     = "\u001B[38;5;20m" ;
    public final static String BROWN     = "\u001B[38;5;130m";
    public final static String BERRY     = "\u001B[38;5;54m" ;
    public final static String PLUM      = "\u001B[38;5;52m" ;
    public final static String PEACH     = "\u001B[38;5;215m";
    public final static String CHERRY    = "\u001B[38;5;1m"  ;
    public final static String CANDY     = "\u001B[38;5;9m"  ;
    public final static String OLIVE     = "\u001B[38;5;106m";
    public final static String SUNSET    = "\u001B[38;5;166m";
    public final static String SCARLET   = "\u001B[38;5;160m";
    public final static String LAKE      = "\u001B[38;5;117m";
    public final static String TOPAZ     = "\u001B[38;5;115m";
    public final static String RETRO     = "\u001B[38;5;144m";
    public final static String NOIR      = "\u001B[38;5;237m";
    public final static String TURQUOISE = "\u001B[38;5;45m";
    public final static String VIOLET    = "\u001B[38;5;135m";
    public final static String GOLD      = "\u001B[38;5;220m";
    public final static String SILVER    = "\u001B[38;5;7m";
    public final static String CORAL     = "\u001B[38;5;209m";
    public final static String LAVENDER  = "\u001B[38;5;183m";
    public final static String MINT      = "\u001B[38;5;121m";
    public final static String TEAL      = "\u001B[38;5;30m";
    public final static String INDIGO    = "\u001B[38;5;54m";
    public final static String MAROON    = "\u001B[38;5;88m";
    public final static String NAVY      = "\u001B[38;5;17m";
    public final static String KHAKI     = "\u001B[38;5;143m";
    public final static String SLATE     = "\u001B[38;5;66m";
    public final static String CRIMSON   = "\u001B[38;5;161m";
    public final static String AMBER     = "\u001B[38;5;172m";
    public final static String EMERALD   = "\u001B[38;5;35m";
    public final static String RUBY      = "\u001B[38;5;197m";
    public final static String SAPPHIRE  = "\u001B[38;5;25m";
    public final static String JADE      = "\u001B[38;5;42m";
    public final static String AMETHYST  = "\u001B[38;5;91m";

    // Text formatting
    public final static String RESET      = "\u001B[0m";
    public final static String BOLD       = "\u001B[1m";
    public final static String DIM        = "\u001B[2m";
    public final static String ITALIC     = "\u001B[3m";
    public final static String UNDERLINE  = "\u001B[4m";
    public final static String BLINK      = "\u001B[5m";
    public final static String REVERSE    = "\u001B[7m";
    public final static String HIDDEN     = "\u001B[8m";
    public final static String STRIKETHROUGH = "\u001B[9m";

    // Reset specific formatting
    public final static String RESET_BOLD       = "\u001B[22m";
    public final static String RESET_DIM        = "\u001B[22m";
    public final static String RESET_ITALIC     = "\u001B[23m";
    public final static String RESET_UNDERLINE  = "\u001B[24m";
    public final static String RESET_BLINK      = "\u001B[25m";
    public final static String RESET_REVERSE    = "\u001B[27m";
    public final static String RESET_HIDDEN     = "\u001B[28m";

    // ========== Utility Methods ==========

    /** Reset all formatting and colors */
    public static void reset() {
        System.out.print(RESET);
    }

    /** Print text with color and reset */
    public static void print(String color, String text) {
        System.out.print(color + text + RESET);
    }

    /** Print line with color and reset */
    public static void println(String color, String text) {
        System.out.println(color + text + RESET);
    }

    /** Print bold text */
    public static void printBold(String text) {
        System.out.print(BOLD + text + RESET);
    }

    /** Print underlined text */
    public static void printUnderline(String text) {
        System.out.print(UNDERLINE + text + RESET);
    }

    /** Print italic text */
    public static void printItalic(String text) {
        System.out.print(ITALIC + text + RESET);
    }

    /** Colorize text and return string (doesn't print) */
    public static String colorize(String color, String text) {
        return color + text + RESET;
    }

    /** Format text with style and return string */
    public static String format(String text, String... styles) {
        StringBuilder sb = new StringBuilder();
        for (String style : styles) {
            sb.append(style);
        }
        sb.append(text).append(RESET);
        return sb.toString();
    }

    // ========== Screen Helpers ==========

    /** Clear entire screen and move cursor to home */
    public static void clear() {
        System.out.print("\u001B[2J\u001B[H");
    }

    /** Clear from cursor to end of line */
    public static void clearEOL() {
        System.out.print("\u001B[0K");
    }

    /** Clear from cursor to beginning of line */
    public static void clearBOL() {
        System.out.print("\u001B[1K");
    }

    /** Clear entire line */
    public static void clearLine() {
        System.out.print("\u001B[2K");
    }

    /** Clear from cursor to end of screen */
    public static void clearEOS() {
        System.out.print("\u001B[0J");
    }

    /** Clear from cursor to beginning of screen */
    public static void clearBOS() {
        System.out.print("\u001B[1J");
    }

    /** Save current screen state */
    public static void screenSave() {
        System.out.print("\u001B[?47h");
    }

    /** Restore saved screen state */
    public static void screenRestore() {
        System.out.print("\u001B[?47l");
    }

    /** Scroll screen up by n lines */
    public static void scrollUp(int n) {
        System.out.print("\u001B[" + n + "S");
    }

    /** Scroll screen down by n lines */
    public static void scrollDown(int n) {
        System.out.print("\u001B[" + n + "T");
    }

    // ========== Cursor Helpers ==========

    /** Move cursor to home position (1,1) */
    public static void cursorHome() {
        System.out.print("\u001B[H");
    }

    /** Move cursor up by 1 line */
    public static void cursorUp() {
        System.out.print("\u001B[1A");
    }

    /** Move cursor up by n lines */
    public static void cursorUp(int n) {
        System.out.print("\u001B[" + n + "A");
    }

    /** Move cursor down by 1 line */
    public static void cursorDown() {
        System.out.print("\u001B[1B");
    }

    /** Move cursor down by n lines */
    public static void cursorDown(int n) {
        System.out.print("\u001B[" + n + "B");
    }

    /** Move cursor left by 1 position */
    public static void cursorLeft() {
        System.out.print("\u001B[1D");
    }

    /** Move cursor left by n positions */
    public static void cursorLeft(int n) {
        System.out.print("\u001B[" + n + "D");
    }

    /** Move cursor right by 1 position */
    public static void cursorRight() {
        System.out.print("\u001B[1C");
    }

    /** Move cursor right by n positions */
    public static void cursorRight(int n) {
        System.out.print("\u001B[" + n + "C");
    }

    /** Move cursor to specific position (x, y) - 1-based indexing */
    public static void cursorMove(int x, int y) {
        System.out.print("\u001B[" + y + ";" + x + "H");
    }

    /** Set cursor visibility */
    public static void cursorVisible(boolean visible) {
        System.out.print(visible ? "\u001B[?25h" : "\u001B[?25l");
    }

    /** Save current cursor position */
    public static void cursorSave() {
        System.out.print("\u001B[s");
    }

    /** Restore saved cursor position */
    public static void cursorRestore() {
        System.out.print("\u001B[u");
    }

    /** Move cursor to column n */
    public static void cursorToColumn(int n) {
        System.out.print("\u001B[" + n + "G");
    }

    /** Move cursor to next line (beginning) */
    public static void cursorNextLine() {
        System.out.print("\u001B[1E");
    }

    /** Move cursor to next line (beginning) n times */
    public static void cursorNextLine(int n) {
        System.out.print("\u001B[" + n + "E");
    }

    /** Move cursor to previous line (beginning) */
    public static void cursorPrevLine() {
        System.out.print("\u001B[1F");
    }

    /** Move cursor to previous line (beginning) n times */
    public static void cursorPrevLine(int n) {
        System.out.print("\u001B[" + n + "F");
    }

    // ========== Color Helpers ==========

    /** Create 24-bit RGB color code from hex value (e.g., 0xFF5733) */
    public static String makeColor(int rgb) {
        int r = (rgb & 0x00FF0000) >> 16;
        int g = (rgb & 0x0000FF00) >> 8;
        int b = (rgb & 0x000000FF);
        return makeColor(r, g, b);
    }

    /** Create 24-bit RGB color code from R,G,B values (0-255) */
    public static String makeColor(int r, int g, int b) {
        return String.format("\u001B[38;2;%d;%d;%dm", r, g, b);
    }

    /** Create background color from 256-color palette (0-255) */
    public static String makeBgColor256(int code) {
        return String.format("\u001B[48;5;%dm", code);
    }

    /** Create foreground color from 256-color palette (0-255) */
    public static String makeColor256(int code) {
        return String.format("\u001B[38;5;%dm", code);
    }

    /** Create 24-bit RGB background color from hex value */
    public static String makeBgColor(int rgb) {
        int r = (rgb & 0x00FF0000) >> 16;
        int g = (rgb & 0x0000FF00) >> 8;
        int b = (rgb & 0x000000FF);
        return makeBgColor(r, g, b);
    }

    /** Create 24-bit RGB background color from R,G,B values (0-255) */
    public static String makeBgColor(int r, int g, int b) {
        return String.format("\u001B[48;2;%d;%d;%dm", r, g, b);
    }

    /** Set foreground color using hex RGB value */
    public static void setColor(int rgb) {
        int r = (rgb & 0x00FF0000) >> 16;
        int g = (rgb & 0x0000FF00) >> 8;
        int b = (rgb & 0x000000FF);
        setColor(r, g, b);
    }

    /** Set foreground color using R,G,B values (0-255) */
    public static void setColor(int r, int g, int b) {
        System.out.print(makeColor(r, g, b));
    }

    /** Set background color using hex RGB value */
    public static void setBgColor(int rgb) {
        int r = (rgb & 0x00FF0000) >> 16;
        int g = (rgb & 0x0000FF00) >> 8;
        int b = (rgb & 0x000000FF);
        setBgColor(r, g, b);
    }

    /** Set background color using R,G,B values (0-255) */
    public static void setBgColor(int r, int g, int b) {
        System.out.print(makeBgColor(r, g, b));
    }

    /** Set foreground color from 256-color palette (0-255) */
    public static void setColor256(int code) {
        System.out.print(makeColor256(code));
    }

    /** Set background color from 256-color palette (0-255) */
    public static void setBgColor256(int code) {
        System.out.print(makeBgColor256(code));
    }

    // ========== Display Helpers ==========

    /** Print all 256 colors with their codes */
    public static void dumpColors() {
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int code = i * 16 + j;
                System.out.printf("\u001B[48;5;%dm%4d ", code, code);
            }
            System.out.println(RESET);
        }
    }

    /** Print a color palette demo with named colors */
    public static void demoColors() {
        System.out.println("\n" + BOLD + "=== Basic Colors ===" + RESET);
        println(RED, "■ RED");
        println(GREEN, "■ GREEN");
        println(YELLOW, "■ YELLOW");
        println(BLUE, "■ BLUE");
        println(MAGENTA, "■ MAGENTA");
        println(CYAN, "■ CYAN");
        println(WHITE, "■ WHITE");

        System.out.println("\n" + BOLD + "=== Bright Colors ===" + RESET);
        println(BRIGHT_RED, "■ BRIGHT RED");
        println(BRIGHT_GREEN, "■ BRIGHT GREEN");
        println(BRIGHT_YELLOW, "■ BRIGHT YELLOW");
        println(BRIGHT_BLUE, "■ BRIGHT BLUE");
        println(BRIGHT_MAGENTA, "■ BRIGHT MAGENTA");
        println(BRIGHT_CYAN, "■ BRIGHT CYAN");

        System.out.println("\n" + BOLD + "=== Extended Colors ===" + RESET);
        println(ORANGE, "■ Orange");
        println(PINK, "■ Pink");
        println(LIME, "■ Lime");
        println(TURQUOISE, "■ Turquoise");
        println(VIOLET, "■ Violet");
        println(GOLD, "■ Gold");
        println(CORAL, "■ Coral");
        println(LAVENDER, "■ Lavender");
        println(MINT, "■ Mint");
        println(AMBER, "■ Amber");
        println(EMERALD, "■ Emerald");
        println(RUBY, "■ Ruby");
        println(SAPPHIRE, "■ Sapphire");

        System.out.println("\n" + BOLD + "=== Text Formatting ===" + RESET);
        System.out.println(BOLD + "Bold Text" + RESET);
        System.out.println(DIM + "Dim Text" + RESET);
        System.out.println(ITALIC + "Italic Text" + RESET);
        System.out.println(UNDERLINE + "Underlined Text" + RESET);
        System.out.println(REVERSE + "Reversed Text" + RESET);
        System.out.println(STRIKETHROUGH + "Strikethrough Text" + RESET);

        System.out.println("\n" + BOLD + "=== Combined Styles ===" + RESET);
        System.out.println(format("Bold + Red", BOLD, RED));
        System.out.println(format("Underline + Green", UNDERLINE, GREEN));
        System.out.println(format("Bold + Underline + Blue", BOLD, UNDERLINE, BLUE));
        System.out.println(RED + BG_YELLOW + "Red on Yellow Background" + RESET);
        System.out.println(BRIGHT_WHITE + BG_BLUE + "White on Blue Background" + RESET);
        System.out.println();
    }

    /** Print a progress bar */
    public static void progressBar(int percent, int width) {
        int filled = (int) (width * percent / 100.0);
        System.out.print("[");
        for (int i = 0; i < width; i++) {
            if (i < filled) {
                System.out.print(GREEN + "█" + RESET);
            } else {
                System.out.print(DIM + "░" + RESET);
            }
        }
        System.out.printf("] %3d%%", percent);
    }

    /** Print a horizontal line */
    public static void printLine(int length, char c) {
        for (int i = 0; i < length; i++) {
            System.out.print(c);
        }
        System.out.println();
    }

    /** Print a colored horizontal line */
    public static void printLine(String color, int length, char c) {
        System.out.print(color);
        printLine(length, c);
        System.out.print(RESET);
    }

    /** Print a box around text */
    public static void printBox(String text) {
        int len = text.length();
        printLine(len + 4, '─');
        System.out.println("│ " + text + " │");
        printLine(len + 4, '─');
    }

    /** Print success message in green */
    public static void success(String message) {
        println(BRIGHT_GREEN, "✓ " + message);
    }

    /** Print error message in red */
    public static void error(String message) {
        println(BRIGHT_RED, "✗ " + message);
    }

    /** Print warning message in yellow */
    public static void warning(String message) {
        println(BRIGHT_YELLOW, "⚠ " + message);
    }

    /** Print info message in cyan */
    public static void info(String message) {
        println(BRIGHT_CYAN, "ℹ " + message);
    }

    // ========== Gradient Support ==========

    /**
     * Interpolate between two RGB color values
     * @param r1 Red component of first color (0-255)
     * @param g1 Green component of first color (0-255)
     * @param b1 Blue component of first color (0-255)
     * @param r2 Red component of second color (0-255)
     * @param g2 Green component of second color (0-255)
     * @param b2 Blue component of second color (0-255)
     * @param t Interpolation factor (0.0 to 1.0)
     * @return Array containing [r, g, b] of interpolated color
     */
    private static int[] interpolateRGB(int r1, int g1, int b1, int r2, int g2, int b2, double t) {
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return new int[]{r, g, b};
    }

    /**
     * Generate a color gradient between two RGB colors
     * @param startRGB Starting color as hex (e.g., 0xFF0000 for red)
     * @param endRGB Ending color as hex (e.g., 0x0000FF for blue)
     * @param steps Number of color steps in the gradient
     * @return Array of color strings for each step
     */
    public static String[] gradient(int startRGB, int endRGB, int steps) {
        int r1 = (startRGB >> 16) & 0xFF;
        int g1 = (startRGB >> 8) & 0xFF;
        int b1 = startRGB & 0xFF;

        int r2 = (endRGB >> 16) & 0xFF;
        int g2 = (endRGB >> 8) & 0xFF;
        int b2 = endRGB & 0xFF;

        String[] colors = new String[steps];
        for (int i = 0; i < steps; i++) {
            double t = steps > 1 ? (double) i / (steps - 1) : 0;
            int[] rgb = interpolateRGB(r1, g1, b1, r2, g2, b2, t);
            colors[i] = makeColor(rgb[0], rgb[1], rgb[2]);
        }
        return colors;
    }

    /**
     * Generate a color gradient between two RGB colors (R,G,B components)
     * @param r1 Red component of start color (0-255)
     * @param g1 Green component of start color (0-255)
     * @param b1 Blue component of start color (0-255)
     * @param r2 Red component of end color (0-255)
     * @param g2 Green component of end color (0-255)
     * @param b2 Blue component of end color (0-255)
     * @param steps Number of color steps in the gradient
     * @return Array of color strings for each step
     */
    public static String[] gradient(int r1, int g1, int b1, int r2, int g2, int b2, int steps) {
        String[] colors = new String[steps];
        for (int i = 0; i < steps; i++) {
            double t = steps > 1 ? (double) i / (steps - 1) : 0;
            int[] rgb = interpolateRGB(r1, g1, b1, r2, g2, b2, t);
            colors[i] = makeColor(rgb[0], rgb[1], rgb[2]);
        }
        return colors;
    }

    /**
     * Print text with a horizontal gradient
     * @param text Text to print with gradient
     * @param startRGB Starting color as hex
     * @param endRGB Ending color as hex
     */
    public static void printGradient(String text, int startRGB, int endRGB) {
        String[] colors = gradient(startRGB, endRGB, text.length());
        for (int i = 0; i < text.length(); i++) {
            System.out.print(colors[i] + text.charAt(i));
        }
        System.out.print(RESET);
    }

    /**
     * Print text with a horizontal gradient (R,G,B components)
     * @param text Text to print with gradient
     * @param r1 Red component of start color (0-255)
     * @param g1 Green component of start color (0-255)
     * @param b1 Blue component of start color (0-255)
     * @param r2 Red component of end color (0-255)
     * @param g2 Green component of end color (0-255)
     * @param b2 Blue component of end color (0-255)
     */
    public static void printGradient(String text, int r1, int g1, int b1, int r2, int g2, int b2) {
        String[] colors = gradient(r1, g1, b1, r2, g2, b2, text.length());
        for (int i = 0; i < text.length(); i++) {
            System.out.print(colors[i] + text.charAt(i));
        }
        System.out.print(RESET);
    }

    /**
     * Print text with a horizontal gradient and newline
     * @param text Text to print with gradient
     * @param startRGB Starting color as hex
     * @param endRGB Ending color as hex
     */
    public static void printlnGradient(String text, int startRGB, int endRGB) {
        printGradient(text, startRGB, endRGB);
        System.out.println();
    }

    /**
     * Print text with a horizontal gradient and newline (R,G,B components)
     * @param text Text to print with gradient
     * @param r1 Red component of start color (0-255)
     * @param g1 Green component of start color (0-255)
     * @param b1 Blue component of start color (0-255)
     * @param r2 Red component of end color (0-255)
     * @param g2 Green component of end color (0-255)
     * @param b2 Blue component of end color (0-255)
     */
    public static void printlnGradient(String text, int r1, int g1, int b1, int r2, int g2, int b2) {
        printGradient(text, r1, g1, b1, r2, g2, b2);
        System.out.println();
    }

    /**
     * Generate a rainbow gradient
     * @param steps Number of color steps
     * @return Array of rainbow colors
     */
    public static String[] rainbowGradient(int steps) {
        String[] colors = new String[steps];
        for (int i = 0; i < steps; i++) {
            double hue = (double) i / steps;
            int[] rgb = hsvToRgb(hue, 1.0, 1.0);
            colors[i] = makeColor(rgb[0], rgb[1], rgb[2]);
        }
        return colors;
    }

    /**
     * Print text with rainbow gradient
     * @param text Text to display
     */
    public static void printRainbow(String text) {
        String[] colors = rainbowGradient(text.length());
        for (int i = 0; i < text.length(); i++) {
            System.out.print(colors[i] + text.charAt(i));
        }
        System.out.print(RESET);
    }

    /**
     * Print text with rainbow gradient and newline
     * @param text Text to display
     */
    public static void printlnRainbow(String text) {
        printRainbow(text);
        System.out.println();
    }

    /**
     * Convert HSV to RGB
     * @param h Hue (0.0 to 1.0)
     * @param s Saturation (0.0 to 1.0)
     * @param v Value/Brightness (0.0 to 1.0)
     * @return Array containing [r, g, b] values (0-255)
     */
    private static int[] hsvToRgb(double h, double s, double v) {
        int hi = (int) (h * 6);
        double f = h * 6 - hi;
        double p = v * (1 - s);
        double q = v * (1 - f * s);
        double t = v * (1 - (1 - f) * s);

        double r, g, b;
        switch (hi % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            case 5: r = v; g = p; b = q; break;
            default: r = g = b = 0; break;
        }

        return new int[]{(int) (r * 255), (int) (g * 255), (int) (b * 255)};
    }

    /**
     * Print a horizontal gradient bar
     * @param width Width of the bar in characters
     * @param startRGB Starting color
     * @param endRGB Ending color
     * @param c Character to use for the bar
     */
    public static void printGradientBar(int width, int startRGB, int endRGB, char c) {
        String[] colors = gradient(startRGB, endRGB, width);
        for (int i = 0; i < width; i++) {
            System.out.print(colors[i] + c);
        }
        System.out.println(RESET);
    }

    /**
     * Print a rainbow bar
     * @param width Width of the bar in characters
     * @param c Character to use for the bar
     */
    public static void printRainbowBar(int width, char c) {
        String[] colors = rainbowGradient(width);
        for (int i = 0; i < width; i++) {
            System.out.print(colors[i] + c);
        }
        System.out.println(RESET);
    }

    /**
     * Render text with a horizontal multi-stop RGB gradient, one color per character.
     *
     * <p>Each element of {@code stops} is an {@code int[3]} of the form
     * {@code {r, g, b}} (0-255). Colors are linearly interpolated between
     * consecutive stops across the full width of {@code text}.</p>
     *
     * <p>Example — white → cyan → blue → purple:</p>
     * <pre>
     *   int[][] g = {{255,255,255},{0,220,220},{30,80,220},{148,0,200}};
     *   System.out.println(ANSI.gradientLine("Hello, World!", g));
     * </pre>
     *
     * @param text  the string to colorize
     * @param stops two or more RGB color stops; must have length &ge; 2
     * @return the text with per-character 24-bit ANSI color codes prepended,
     *         followed by {@link #RESET}
     */
    public static String gradientLine(String text, int[][] stops) {
        if (text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder();
        int n = text.length();
        int segments = stops.length - 1;
        for (int i = 0; i < n; i++) {
            double t      = (double) i / Math.max(1, n - 1);
            double scaled = t * segments;
            int seg       = Math.min((int) scaled, segments - 1);
            double segT   = scaled - seg;
            int r = (int) Math.round(stops[seg][0] + segT * (stops[seg+1][0] - stops[seg][0]));
            int g = (int) Math.round(stops[seg][1] + segT * (stops[seg+1][1] - stops[seg][1]));
            int b = (int) Math.round(stops[seg][2] + segT * (stops[seg+1][2] - stops[seg][2]));
            sb.append(makeColor(r, g, b)).append(text.charAt(i));
        }
        sb.append(RESET);
        return sb.toString();
    }

    /**
     * Demo gradient features
     */
    public static void demoGradients() {
        System.out.println("\n" + BOLD + "=== Gradient Demos ===" + RESET);

        // Simple gradients
        System.out.print("Red to Blue: ");
        printlnGradient("This is a gradient from red to blue!", 0xFF0000, 0x0000FF);

        System.out.print("Green to Yellow: ");
        printlnGradient("Spring green to golden yellow gradient", 0x00FF00, 0xFFFF00);

        System.out.print("Purple to Cyan: ");
        printlnGradient("Purple dreams into cyan skies", 0x9B59B6, 0x00FFFF);

        // Rainbow
        System.out.print("Rainbow: ");
        printlnRainbow("Rainbow colors are absolutely beautiful!");

        // Gradient bars
        System.out.println("\nGradient Bars:");
        System.out.print("Fire: ");
        printGradientBar(60, 0xFF0000, 0xFFFF00, '█');

        System.out.print("Ocean: ");
        printGradientBar(60, 0x0000FF, 0x00FFFF, '█');

        System.out.print("Sunset: ");
        printGradientBar(60, 0xFF4500, 0xFF1493, '█');

        System.out.print("Rainbow: ");
        printRainbowBar(60, '█');

        System.out.println();
    }
}
