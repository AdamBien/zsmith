package airhacks.zsmith.tools.control;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.concurrent.locks.ReentrantLock;

import airhacks.zsmith.logging.control.Log;

public class Console {

    static BufferedReader stdin;

    /// One question at a time. Tools of the same turn run beside each other, and two of them
    /// asking at once would leave the user typing an answer without knowing which question
    /// receives it. A lock rather than `synchronized`: the wait lasts as long as a person
    /// takes to answer, and the waiting tools are on virtual threads.
    static final ReentrantLock ASKING = new ReentrantLock();

    public static String prompt(String message) {
        ASKING.lock();
        try {
            Log.user(message);
            var line = readLine();
            return line == null ? "" : line.trim();
        } finally {
            ASKING.unlock();
        }
    }

    /// System.console() is null when stdin/stdout are redirected (pipes, CI, child
    /// processes) — fall back to reading System.in directly so prompts still work there.
    /// A null line (EOF) is mapped to an empty answer by prompt().
    static String readLine() {
        var console = System.console();
        if (console != null) {
            return console.readLine();
        }
        if (stdin == null) {
            stdin = new BufferedReader(new InputStreamReader(System.in));
        }
        try {
            return stdin.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read user input", e);
        }
    }
}
