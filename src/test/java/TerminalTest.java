import java.io.IOException;
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

public class TerminalTest {

    static void printBallAt(int row, int column) {
        Terminal.clearScreen();
        Terminal.printText(1, 1, "Press q to quit; use the arrow keys to move the ball");
        Terminal.printText(row + 0, column, "+-+");
        Terminal.printText(row + 1, column, "| |");
        Terminal.printText(row + 2, column, "+-+");
    }

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

        for (;;) {
            printBallAt(ballRow, ballColumn);
            int errorCount = 0;
            int c = Terminal.readByte();
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
                        default -> { Terminal.printText(1 + ++errorCount, 1, "Unexpected escape sequence \"\\033[" + byteToString(c2) + "\"."); }
                    }
                } else
                    Terminal.printText(1 + ++errorCount, 1, "Unexpected escape sequence \"\\033" + byteToString(c1) + "\".");
            } else
                Terminal.printText(1 + ++errorCount, 1, "Unexpected input byte \'" + byteToString(c) + "\'.");
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
