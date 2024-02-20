package io.github.btj.termios;

public class Terminal {
    
    private static java.io.FileInputStream stdin;
    
    static {
        System.loadLibrary("io_github_btj_termios");
        stdin = new java.io.FileInputStream(java.io.FileDescriptor.in);
    }

    /**
     * Put the terminal in a mode where it reports
     * key presses immediately rather than buffering them
     * until Return is pressed or otherwise processing them.
     */
    public static native void enterRawInputMode();
    
    /**
     * Read a byte from standard input.
     *
     * This may be an ASCII character, (a part of) a
     * (presumably UTF-8-encoded) non-ASCII character,
     * or (part of) a control sequence. Run the raw input test
     * to experimentally determine which bytes are generated
     * by which keypresses.
     */
    public static int readByte() throws java.io.IOException {
        return stdin.read();
    }

    /**
     * Put the terminal back into the mode that was active
     * when enterRawInputMode was last called.
     */
    public static native void leaveRawInputMode();

    /**
     * Requests the terminal to report the size of the text area to standard input.
     *
     * Will be reported as <code>CSI 8 ; height ; width t</code> where
     * CSI is <code>"\033["</code>.
     */
    public static void reportTextAreaSize() {
        System.out.print("\033[18t");
        System.out.flush();
    }

    /**
     * Requests the terminal to clear the screen.
     */
    public static void clearScreen() {
        System.out.print("\033[2J");
        System.out.print("\033[3J");
        System.out.flush();
    }

    /**
     * Requests the terminal to move the cursor to the given row and column (1-based).
     *
     * @throws IllegalArgumentException if the given row or column are less than 1.
     */
    public static void moveCursor(int row, int column, String text) {
        if (row < 1)
            throw new IllegalArgumentException("The given row is less than 1");
        if (column < 1)
            throw new IllegalArgumentException("The given column is less than 1");

        System.out.format("\033[%d;%dH", row, column);
        System.out.flush();
    }

    /**
     * Requests the terminal to print the given text at the given row and column (both 1-based).
     * This also moves the cursor, but if the last character printed is in the last column of the text area, the resulting cursor position is not well-defined.
     *
     * Note: printing to the last column of the last row of the text area might cause the terminal to scroll and should therefore be avoided.
     *
     * @throws IllegalArgumentException if the given row or column are less than 1.
     * @throws IllegalArgumentException if the given text contains characters whose value is less than 32 or greater than 126.
     */
    public static void printText(int row, int column, String text) {
        if (row < 1)
            throw new IllegalArgumentException("The given row is less than 1");
        if (column < 1)
            throw new IllegalArgumentException("The given column is less than 1");
        if (text.chars().anyMatch(c -> c < 32 || 126 < c))
            throw new IllegalArgumentException("The given text contains control characters or non-ASCII characters");

        System.out.format("\033[%d;%dH%s", row, column, text);
        System.out.flush();
    }

}
