package airhacks.zsmith.logging.control;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Pattern;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.logging.boundary.LogSink;
import airhacks.zsmith.logging.entity.Sink;

/// The one destination this JVM's routable channels are going to.
///
/// Synchronized because opening is a check followed by several steps before anything is writing,
/// and because the channels themselves arrive from tool threads while the loop is still emitting.
public class Destinations {

    static final String LOGS_DIRECTORY = "logs";

    /// Colour is for a terminal. A file that is going to be read with `grep` keeps the words.
    static final Pattern ANSI = Pattern.compile("\\[[0-9;]*m");

    /// Guarded by this class's monitor.
    private static PrintWriter file;
    private static Sink sink = Sink.CONSOLE;

    private Destinations() {
    }

    public static synchronized Optional<Path> direct(String agentName) {
        var configured = Sink.of(ZCfg.string(LogSink.SINK, null));
        if (!configured.writesToFile()) {
            return Optional.empty();
        }
        if (file != null) {
            return Optional.empty();
        }
        if (agentName == null || agentName.isBlank()) {
            Log.warning("diagnostics need an agent name to file the log under");
            return Optional.empty();
        }
        try {
            var destination = destinationFor(agentName);
            Files.createDirectories(destination.getParent());
            file = new PrintWriter(Files.newBufferedWriter(destination), true);
            sink = configured;
            Log.agent("diagnostics going to " + destination);
            return Optional.of(destination);
        } catch (IOException | RuntimeException e) {
            // the run is not the thing being observed's problem
            Log.warning("could not open a log for " + agentName + ", keeping diagnostics on the console: "
                    + e.getMessage());
            file = null;
            sink = Sink.CONSOLE;
            return Optional.empty();
        }
    }

    public static synchronized void release() {
        if (file == null) {
            return;
        }
        file.flush();
        file.close();
        file = null;
        sink = Sink.CONSOLE;
    }

    /// Routes one already-formatted line. Falls back to the console whenever no file is open, so
    /// everything emitted before an agent names itself still reaches somebody.
    static synchronized void write(PrintStream console, String text, boolean newline) {
        var destination = file == null ? Sink.CONSOLE : sink;
        if (destination.writesToConsole()) {
            print(console, text, newline);
        }
        if (destination.writesToFile()) {
            var plain = ANSI.matcher(text).replaceAll("");
            if (newline) {
                file.println(plain);
            } else {
                file.print(plain);
            }
        }
    }

    static void print(PrintStream console, String text, boolean newline) {
        if (newline) {
            console.println(text);
        } else {
            console.print(text);
        }
    }

    /// Named by process, beside the agent's recordings and memories, because two runs of one agent
    /// overlap routinely and the second must not land on the first.
    static Path destinationFor(String agentName) {
        return ZCfg.agentDirectory(agentName)
                .resolve(LOGS_DIRECTORY)
                .resolve("%s-%d.log".formatted(agentName, ProcessHandle.current().pid()));
    }
}
