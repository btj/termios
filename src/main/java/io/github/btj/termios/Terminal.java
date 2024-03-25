package io.github.btj.termios;

import java.util.concurrent.TimeoutException;

class IntBufferPoison extends Exception {
    IntBufferPoison(Throwable throwable) {
        super(throwable);
    }
}

class IntBuffer {
    private static final int SIZE_BITS = 10;
    private static final int SIZE = 1 << SIZE_BITS;
    private static final int SIZE_MASK = SIZE - 1;

    private final int[] buffer = new int[SIZE];
    private int start;
    private int length;
    private Throwable poison;

    public synchronized void put(int element) throws InterruptedException {
        while (length == SIZE)
            wait();
        buffer[(start + length++) & SIZE_MASK] = element;
        notifyAll();
    }

    public synchronized void poison(Throwable poison) {
        this.poison = poison;
        notifyAll();
    }

    public synchronized int take(long deadline) throws InterruptedException, IntBufferPoison, TimeoutException {
        while (length == 0) {
            if (poison != null)
                throw new IntBufferPoison(poison);
            long currentTime = System.currentTimeMillis();
            if (deadline <= currentTime)
                throw new TimeoutException();
            wait(deadline - currentTime);
        }
        int result = buffer[start++];
        start &= SIZE_MASK;
        length--;
        notifyAll();
        return result;
    }
}

class Stdin {

    private static final IntBuffer buffer = new IntBuffer();

    static {
        Thread t = new Thread(null, null, "io.github.btj.termios.Stdin pump") {
            java.io.FileInputStream stdin = new java.io.FileInputStream(java.io.FileDescriptor.in);
            public void run() {
                try {
                    for (;;) {
                        int b = stdin.read();
                        buffer.put(b);
                    }
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                } catch (java.io.IOException e) {
                    buffer.poison(e);
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    static int readByte(long deadline) throws java.io.IOException, java.util.concurrent.TimeoutException {
        try {
            return buffer.take(deadline);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        } catch (IntBufferPoison poison) {
            if (poison.getCause() instanceof java.io.IOException e)
                throw e;
            throw new AssertionError("Unexpected poison", poison);
        }
    }

    static int readByte() throws java.io.IOException {
        try {
            return readByte(Long.MAX_VALUE);
        } catch (TimeoutException e) {
            throw new AssertionError();
        }
    }

}

public class Terminal {
    
    static {
        try {
            String libName = System.mapLibraryName("io_github_btj_termios");
            byte[] libBytes = Terminal.class.getClassLoader().getResourceAsStream(libName).readAllBytes();
            byte[] libMD5Bytes = java.security.MessageDigest.getInstance("MD5").digest(libBytes);
            String libMD5 = new java.math.BigInteger(1, libMD5Bytes).toString(16);
            String libPath = System.getProperty("java.io.tmpdir") + "/" + System.mapLibraryName("io_github_btj_termios_" + libMD5);
            java.io.File libFile = new java.io.File(libPath);
            if (!libFile.exists())
                java.nio.file.Files.write(libFile.toPath(), libBytes);
            //System.out.println("The library is at " + libPath);
            System.load(libPath);
        } catch (java.io.IOException|java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

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
        return Stdin.readByte();
    }
    
    /**
     * Read a byte from standard input.
     *
     * This may be an ASCII character, (a part of) a
     * (presumably UTF-8-encoded) non-ASCII character,
     * or (part of) a control sequence. Run the raw input test
     * to experimentally determine which bytes are generated
     * by which keypresses.
     * 
     * Throws a TimeoutException if the specified deadline (in milliseconds since midnight, January 1, 1970 UTC)
     * passed without a byte becoming available.
     */
    public static int readByte(long deadline) throws java.io.IOException, java.util.concurrent.TimeoutException {
        return Stdin.readByte(deadline);
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
    public static void moveCursor(int row, int column) {
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

    private Terminal() {}

}
