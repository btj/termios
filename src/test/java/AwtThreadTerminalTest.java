import javax.swing.JFrame;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

import io.github.btj.termios.Terminal;

class TerminalParser {

    int buffer;
    boolean bufferFull;

    int peekByte() throws IOException {
        if (!bufferFull) {
            buffer = Terminal.readByte();
            bufferFull = true;
        }
        return buffer;
    }

    void eatByte() throws IOException {
        peekByte();
        bufferFull = false;
    }

    void expect(int c) throws IOException {
        if (peekByte() != c)
            throw new AssertionError("Unexpected byte " + c + " on standard input");
        eatByte();
    }

    int expectNumber() throws IOException {
        int c = peekByte();
        if (c < '0' || '9' < c)
            throw new AssertionError("Digit expected, but got " + c);
        int result = c - '0';
        eatByte();
        for (;;) {
            c = peekByte();
            if (c < '0' || '9' < c)
                break;
            eatByte();
            if (result > (Integer.MAX_VALUE - (c - '0')) / 10)
                throw new AssertionError("Overflow while reading number");
            result = result * 10 + (c - '0');
        }
        return result;
    }
}

class ScreenBuffer {
    private final int height, width;
    private char[] buffer;

    ScreenBuffer(int height, int width) {
        this.height = height;
        this.width = width;
        buffer = new char[height * width];
    }

    void clear() {
        java.util.Arrays.fill(buffer, ' ');
    }

    /**
     * @param row 0-based
     * @param column 0-based
     */
    void setCell(int row, int column, char c) {
        buffer[row * width + column] = c;
    }

    /** Assumes the text fits on the line.
     * @param row 0-based
     * @param column 0-based
     */
    void writeString(int row, int column, String text) {
        text.getChars(0, text.length(), buffer, row * width + column);
    }

    void printBallAt(int row, int column) {
        writeString(row + 0, column, "+-+");
        writeString(row + 1, column, "| |");
        writeString(row + 2, column, "+-+");
    }

    void flush() {
        for (int i = 0; i < height; i++)
            Terminal.printText(1 + i, 1, String.valueOf(buffer, i * width, width));
    }
}

interface FallibleRunnable {
    void run() throws Throwable;
}

public class AwtThreadTerminalTest {

    static String byteToString(int c) {
        if (32 <= c && c <= 126)
            return "" + (char)c;
        return String.format("\\x%02x", c);
    }

    static void handleFailure(FallibleRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            t.printStackTrace();
            Terminal.leaveRawInputMode();
            System.exit(1);
        }
    }

    static void runTestApp() throws IOException {
        Terminal.reportTextAreaSize();
        TerminalParser parser = new TerminalParser();
        parser.expect('\033');
        parser.expect('[');
        parser.expect('8');
        parser.expect(';');
        int height = parser.expectNumber();
        parser.expect(';');
        int width = parser.expectNumber();
        parser.expect('t');
        
        // Create a dummy JFrame. Serves only to keep the AWT event handling thread alive.
        // See https://docs.oracle.com/en%2Fjava%2Fjavase%2F21%2Fdocs%2Fapi%2F%2F/java.desktop/java/awt/doc-files/AWTThreadIssues.html#auto-shutdown-heading
        JFrame dummyFrame = new JFrame();
        dummyFrame.pack();
        
        class TestApp {
            int ballRow = height / 2 - 1;
            int ballColumn = width / 2 - 1;

            ScreenBuffer buffer = new ScreenBuffer(height, width);
            ArrayList<String> errors = new ArrayList<>();
            long startTime = System.currentTimeMillis();

            TestApp() throws IOException {
                javax.swing.Timer timer = new javax.swing.Timer(1000, e ->
                    handleFailure(() -> updateScreen())
                );
                timer.start();
                Terminal.setInputListener(new Runnable() {
                    public void run() {
                        java.awt.EventQueue.invokeLater(() -> handleFailure(() -> {
                            if (!java.awt.EventQueue.isDispatchThread())
                                throw new AssertionError("Should run in AWT dispatch thread!");
                            int c = Terminal.readByte();
                            if (c == 'q') {
                                Terminal.leaveRawInputMode();
                                System.exit(0);
                            }
                            if (c == '\033') {
                                int c1 = Terminal.readByte();
                                if (c1 == '[') {
                                    int c2 = Terminal.readByte();
                                    switch (c2) {
                                        case 'A' -> { if (ballRow > 1) ballRow--; }
                                        case 'B' -> { if (ballRow < height - 2) ballRow++; }
                                        case 'C' -> { if (ballColumn < width - 2) ballColumn++; }
                                        case 'D' -> { if (ballColumn > 1) ballColumn--; }
                                        default -> { errors.add("Unexpected escape sequence \"\\033[" + byteToString(c2) + "\"."); }
                                    }
                                } else
                                    errors.add("Unexpected escape sequence \"\\033" + byteToString(c1) + "\".");
                            } else
                                errors.add("Unexpected input byte \'" + byteToString(c) + "\'.");
                            updateScreen();
                            Terminal.setInputListener(this);
                        }));
                    }
                });
                updateScreen();
            }

            void updateScreen() throws IOException {
                if (!java.awt.EventQueue.isDispatchThread())
                    throw new AssertionError("Should run in AWT dispatch thread!");
                buffer.clear();
                buffer.writeString(0, 0, "Press q to quit; use the arrow keys to move the ball");
                long currentTime = System.currentTimeMillis();
                long secondsPassed = (currentTime - startTime) / 1000;
                String timeMessage =  secondsPassed + "s passed";
                buffer.writeString(0, width - timeMessage.length(), timeMessage);
                for (int i = 0; i < errors.size(); i++)
                    buffer.writeString(i + 1, 0, errors.get(i));
                buffer.printBallAt(ballRow - 1, ballColumn - 1);
                buffer.flush();
                Terminal.moveCursor(ballRow, ballColumn);
            }
        }
        new TestApp();
    }

    public static void awtMain(String[] args) {
        if (!java.awt.EventQueue.isDispatchThread())
            throw new AssertionError("Should run in AWT dispatch thread!");
        Terminal.enterRawInputMode();
        handleFailure(() -> runTestApp());
    }

    public static void main(String[] args) {
        /*
         * Note: it turns out that, at least on MacOS, using the
         * AWT thread causes AWT to be activated and the terminal
         * window to lose OS-level focus,
         * which means the user has to click the terminal window
         * to continue interacting with the app. An (optional)
         * workaround is to start using AWT only when you actually
         * want to show a GUI, and until then use
         * the old approach with blocking readByte calls.
         */
        java.awt.EventQueue.invokeLater(() -> awtMain(args));
    }
}
