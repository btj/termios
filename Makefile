# See https://www.baeldung.com/jni

ifndef JAVA_HOME
  $(error Please set JAVA_HOME to the path to your JDK)
endif

ifdef WINDIR
  CFLAGS=
  JNI_INCLUDE_DIR=win32
  LIBNAME=io_github_btj_termios.dll
  SHARED_FLAG=-shared
  LINKFLAGS=-Wl,--add-stdcall-alias
  PATHSEP=;
else
  ifeq ($(shell uname -s), Darwin)
    JNI_INCLUDE_DIR=darwin
    LIBNAME=libio_github_btj_termios.dylib
    SHARED_FLAG=-dynamiclib
    LINKFLAGS=-lc
  else
    JNI_INCLUDE_DIR=linux
    LIBNAME=libio_github_btj_termios.so
    SHARED_FLAG=-shared -fPIC
    LINKFLAGS=-lc
  endif
  CFLAGS=-fPIC
  PATHSEP=:
endif

jar: tests _build/main/io.github.btj.termios.jar

_build/main/io.github.btj.termios.jar: termios
	jar cf _build/main/io.github.btj.termios.jar -C _build/main/classes .

tests: _build/test/classes/TerminalRawInputTest.class _build/test/classes/TerminalTest.class

_build/test/classes/TerminalTest.class: termios src/test/java/TerminalTest.java
	javac -d _build/test/classes -cp _build/main/classes src/test/java/TerminalTest.java

_build/test/classes/TerminalRawInputTest.class: termios src/test/java/TerminalRawInputTest.java
	javac -d _build/test/classes -cp _build/main/classes src/test/java/TerminalRawInputTest.java

.PHONY: termios
termios: _build/main/classes/io/github/btj/termios/Terminal.class _build/main/classes/${LIBNAME}

_build/main/classes/${LIBNAME}: _build/main/io_github_btj_termios_Terminal.o
	gcc ${SHARED_FLAG} -o _build/main/classes/${LIBNAME} _build/main/io_github_btj_termios_Terminal.o ${LINKFLAGS}

_build/main/io_github_btj_termios_Terminal.o: _build/main/include/io_github_btj_termios_Terminal.h src/main/c/io_github_btj_termios_Terminal.c
	gcc -c $(CFLAGS) -I _build/main/include "-I${JAVA_HOME}/include" "-I${JAVA_HOME}/include/${JNI_INCLUDE_DIR}" src/main/c/io_github_btj_termios_Terminal.c -o _build/main/io_github_btj_termios_Terminal.o

_build/main/classes/io/github/btj/termios/Terminal.class _build/main/include/io_github_btj_termios_Terminal.h: src/main/java/io/github/btj/termios/Terminal.java
	javac -h _build/main/include -d _build/main/classes src/main/java/io/github/btj/termios/Terminal.java

test_raw_input: _build/test/classes/TerminalRawInputTest.class
	java -cp "_build/main/classes${PATHSEP}_build/test/classes" TerminalRawInputTest

test: _build/test/classes/TerminalTest.class
	java -cp "_build/main/classes${PATHSEP}_build/test/classes" TerminalTest
