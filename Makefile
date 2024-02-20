# See https://www.baeldung.com/jni

ifndef JAVA_HOME
  $(error Please set JAVA_HOME to the path to your JDK)
endif

tests: _build/test/classes/TerminalRawInputTest.class _build/test/classes/TerminalTest.class

_build/test/classes/TerminalTest.class: termios src/test/java/TerminalTest.java
	javac -d _build/test/classes -cp _build/main/classes src/test/java/TerminalTest.java

_build/test/classes/TerminalRawInputTest.class: termios src/test/java/TerminalRawInputTest.java
	javac -d _build/test/classes -cp _build/main/classes src/test/java/TerminalRawInputTest.java

.PHONY: termios
termios: _build/main/classes/io/github/btj/termios/Terminal.class _build/main/libio_github_btj_termios.dylib

_build/main/libio_github_btj_termios.dylib: _build/main/io_github_btj_termios_Terminal.o
	gcc -dynamiclib -o _build/main/libio_github_btj_termios.dylib _build/main/io_github_btj_termios_Terminal.o -lc

_build/main/io_github_btj_termios_Terminal.o: _build/main/include/io_github_btj_termios_Terminal.h src/main/c/io_github_btj_termios_Terminal.c
	gcc -c -fPIC -I _build/main/include -I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin src/main/c/io_github_btj_termios_Terminal.c -o _build/main/io_github_btj_termios_Terminal.o

_build/main/classes/io/github/btj/termios/Terminal.class _build/main/include/io_github_btj_termios_Terminal.h: src/main/java/io/github/btj/termios/Terminal.java
	javac -h _build/main/include -d _build/main/classes src/main/java/io/github/btj/termios/Terminal.java

test_raw_input: _build/test/classes/TerminalRawInputTest.class
	java -cp _build/main/classes:_build/test/classes -Djava.library.path=_build/main TerminalRawInputTest

test: _build/test/classes/TerminalTest.class
	java -cp _build/main/classes:_build/test/classes -Djava.library.path=_build/main TerminalTest
