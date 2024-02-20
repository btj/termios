#include <stdio.h>
#include "io_github_btj_termios_Terminal.h"

#ifdef _WIN32
    #include <windows.h>

    DWORD oldConsoleMode;
    HANDLE hStdin;

    JNIEXPORT void JNICALL Java_io_github_btj_termios_Terminal_enterRawInputMode(JNIEnv *env, jclass class_) {
        hStdin = GetStdHandle(STD_INPUT_HANDLE);
        if (hStdin == INVALID_HANDLE_VALUE)
            (*env)->FatalError(env, "Couldn't get the stdin handle");

        if (!GetConsoleMode(hStdin, &oldConsoleMode) )
            (*env)->FatalError(env, "Couldn't get the console mode. Is stdin not a console?");

        if (!SetConsoleMode(hStdin, ENABLE_VIRTUAL_TERMINAL_INPUT))
            (*env)->FatalError(env, "Couldn't set the console mode. Is stdin not a console?");
    }

    JNIEXPORT void JNICALL Java_io_github_btj_termios_Terminal_leaveRawInputMode(JNIEnv *env, jclass class_) {
        if (!SetConsoleMode(hStdin, oldConsoleMode))
            (*env)->FatalError(env, "Couldn't set the console mode. Is stdin not a console?");
    }

#else

    #include <termios.h>

    struct termios original_term;

    JNIEXPORT void JNICALL Java_io_github_btj_termios_Terminal_enterRawInputMode(JNIEnv *env, jclass class_) {
        if (tcgetattr(fileno(stdin), &original_term))
            (*env)->FatalError(env, "Could not get the terminal attributes. Is stdin not a terminal?");

        struct termios raw = original_term;

        /* input modes - clear indicated ones giving: no break, no CR to NL,
        no parity check, no strip char, no start/stop output (sic) control */
        //raw.c_iflag &= ~(BRKINT | ICRNL | INPCK | ISTRIP | IXON);
        
        raw.c_iflag &= ~ICRNL; // Without this line, Return key presses are reported as LF; with it, they are reported as CR.
        raw.c_iflag &= ~BRKINT; // Without this line, Ctrl+C is processed by the terminal itself and not delivered to the application.
        raw.c_iflag &= ~IXON; // Without this line, Ctrl+S is processed by the terminal itself and not delivered to the application.

        /* output modes - clear giving: no post processing such as NL to CR+NL */
        //raw.c_oflag &= ~(OPOST);

        /* control modes - set 8 bit chars */
        //raw.c_cflag |= (CS8);

        /* local modes - clear giving: echoing off, canonical off (no erase with
        backspace, ^U,...),  no extended functions, no signal chars (^Z,^C) */
        raw.c_lflag &= ~(ECHO | ICANON | IEXTEN | ISIG);

        /* control chars - set return condition: min number of bytes and timer */
        raw.c_cc[VMIN] = 1; raw.c_cc[VTIME] = 0; /* immediate - anything       */

        if (tcsetattr(fileno(stdin), TCSANOW, &raw))
            (*env)->FatalError(env, "Could not set the terminal attributes. Is stdin not a terminal?");
    }

    JNIEXPORT void JNICALL Java_io_github_btj_termios_Terminal_leaveRawInputMode(JNIEnv *env, jclass class_) {
        if (tcsetattr(fileno(stdin), TCSANOW, &original_term))
            (*env)->FatalError(env, "Could not set the terminal attributes. Is stdin not a terminal?");
    }

#endif
