import java.io.IOException;
import io.github.btj.termios.Terminal;

public class TerminalRawInputTest {

	public static void main(String[] args) throws IOException {
		Terminal.enterRawInputMode();
		
                Terminal.reportTextAreaSize();
                System.out.println("Requested that the terminal report the size of the terminal.");
		System.out.println("Please enter some characters, or enter q to quit:");
		
		for (;;) {
			int c = Terminal.readByte();
			if (c == 'q')
				break;
			if (c == '\033') {
				int c1 = Terminal.readByte();
				if (c1 == '[') {
					int c2 = Terminal.readByte();
                                        System.out.printf("Unknown escape sequence \\033[%c\n", c2);
				} else
					System.out.printf("Unknown escape sequence \\033%c\n", c1);
			} else if (32 <= c && c <= 126)
				System.out.printf("You entered character %d = '%c'\n", c, c);
                        else
				System.out.printf("You entered character %d\n", c);
		}
		
		Terminal.leaveRawInputMode();
		System.out.println("Bye!");
	}

}
