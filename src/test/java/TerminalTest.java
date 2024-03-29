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

public class TerminalTest {

    static String byteToString(int c) {
        if (32 <= c && c <= 126)
            return "" + (char)c;
        return String.format("\\x%02x", c);
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
        
        int ballRow = height / 2 - 1;
        int ballColumn = width / 2 - 1;

        ScreenBuffer buffer = new ScreenBuffer(height, width);
        ArrayList<String> errors = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (;;) {
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
            try {
                long deadline = startTime + (secondsPassed + 1) * 1000;
                int c = Terminal.readByte(deadline);
                if (c == 'q')
                    break;
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
            } catch (TimeoutException e) {
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Terminal.enterRawInputMode();
        try {
            runTestApp();
        } finally {
            Terminal.leaveRawInputMode();
        }
    }
}
