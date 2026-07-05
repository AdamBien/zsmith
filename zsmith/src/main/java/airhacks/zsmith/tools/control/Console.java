package airhacks.zsmith.tools.control;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;

import airhacks.zsmith.logging.control.Log;

public class Console {

    static BufferedReader stdin;

    public static String prompt(String message) {
        Log.user(message);
        var line = readLine();
        return line == null ? "" : line.trim();
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
