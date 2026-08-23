import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.logging.boundary.LogSink;
import airhacks.zsmith.logging.control.Log;
import airhacks.zsmith.logging.control.ProgressBar;
import airhacks.zsmith.telemetry.entity.TokenUsage;

/// Traces logging spec R1.1–R1.3 and R2.1–R2.8 — see
/// src/main/java/airhacks/zsmith/logging/package-info.java
///
/// Console output is captured by swapping the process stream, which works because the channels
/// resolve it at write time rather than holding the one that existed when they were created.

static final String APP = "zsmith-sink-" + ProcessHandle.current().pid();
static final String AGENT = "sink-agent";

static ByteArrayOutputStream console = new ByteArrayOutputStream();
static PrintStream original = System.out;

void main() throws Exception {
    var home = Files.createTempDirectory("zsmith-sink-home");
    System.setProperty("user.home", home.toString());

    presentsRoutableChannelsOnTheConsoleByDefault();
    var written = writesEveryRoutableChannelToTheFile(home);
    keepsTheConversationOnTheConsole();
    leavesASwitchedOffChannelOff(written);
    flushesAndClosesOnRelease(written);
    writesToBothWhenAsked(home);
    reportsAFileItCannotOpen(home);
}

// R2.3 — Where no file destination is in effect, the BC shall present every routable channel on
// the console.
void presentsRoutableChannelsOnTheConsoleByDefault() {
    ZCfg.loadBaseConfig(APP);

    var printed = capturing(() -> Log.agent("before any destination is chosen"));
    if (!printed.contains("before any destination is chosen"))
        throw new AssertionError("R2.3 — a routable channel should reach the console by default, got: " + printed);

    if (LogSink.directDiagnostics(AGENT).isPresent())
        throw new AssertionError("R2.3 — no file should be opened while the sink is unset");
}

// R2.1 — Where diagnostics are directed to a file, the BC shall write every routable channel to
// that file and none of them to the console.
// R2.4 — When diagnostics are directed for a named agent, the BC shall write them under that
// agent's own directory.
// R2.5 — When a diagnostic file is opened, the BC shall name it so that concurrent runs do not
// overwrite one another.
Path writesEveryRoutableChannelToTheFile(Path home) throws Exception {
    System.setProperty(LogSink.SINK, "file");
    ZCfg.loadBaseConfig(APP);

    var destination = LogSink.directDiagnostics(AGENT)
            .orElseThrow(() -> new AssertionError("R2.1 — a file sink did not open a file"));

    // R2.4 — under this agent's own directory
    var agentDirectory = home.resolve("." + APP).resolve(AGENT);
    if (!destination.startsWith(agentDirectory))
        throw new AssertionError("R2.4 — expected the log under " + agentDirectory + " but got " + destination);

    // R2.5 — the process is in the name, so a concurrent run cannot land on this one
    if (!destination.getFileName().toString().contains(String.valueOf(ProcessHandle.current().pid())))
        throw new AssertionError("R2.5 — expected the process in the name but got " + destination.getFileName());

    var printed = capturing(() -> {
        Log.agent("a routable line");
        Log.tool("another routable line");
    });
    if (printed.contains("a routable line") || printed.contains("another routable line"))
        throw new AssertionError("R2.1 — routable channels still reached the console: " + printed);

    var logged = Files.readString(destination);
    if (!logged.contains("a routable line") || !logged.contains("another routable line"))
        throw new AssertionError("R2.1 — the file is missing routable output, holds: " + logged);
    return destination;
}

// R1.1 — The BC shall present what the agent asks and answers on the console, whatever destination
// diagnostics are directed to.
// R1.2 — The BC shall present failures and warnings on the console, whatever destination
// diagnostics are directed to.
// R1.3 — The BC shall present a run's progress on the console, whatever destination diagnostics
// are directed to.
void keepsTheConversationOnTheConsole() {
    // still directed to a file from the previous case
    var printed = capturing(() -> {
        Log.user("Which episode should I announce?");
        Log.answer("Published.");
        Log.prompt("the question that was asked");
        Log.warning("something looks off");
        new ProgressBar(25).summary(new TokenUsage(12400, 1850, 98000, 4200));
    });

    // R1.1 — a question the run blocks on has to be visible to the person expected to answer it
    if (!printed.contains("Which episode should I announce?"))
        throw new AssertionError("R1.1 — the question was redirected away from the user: " + printed);
    if (!printed.contains("Published."))
        throw new AssertionError("R1.1 — the answer was redirected away from the user: " + printed);
    if (!printed.contains("the question that was asked"))
        throw new AssertionError("R1.1 — the prompt was redirected away from the user: " + printed);

    // R1.2 — a failure nobody sees is worse than one that clutters a prompt
    if (!printed.contains("something looks off"))
        throw new AssertionError("R1.2 — the warning was redirected away from the user: " + printed);

    // R1.3 — a long run with a silent terminal is indistinguishable from a hung one
    if (!printed.contains("tokens: in=12400 out=1850 total=116450"))
        throw new AssertionError("R1.3 — the progress summary was redirected away from the user: " + printed);
}

// R2.8 — The BC shall leave a switched-off channel switched off whatever the destination.
void leavesASwitchedOffChannelOff(Path destination) throws Exception {
    var before = Files.readString(destination);
    Log.request("a payload nobody asked for");
    var after = Files.readString(destination);

    if (after.contains("a payload nobody asked for"))
        throw new AssertionError("R2.8 — directing to a file started recording a switched-off channel");
    if (!after.equals(before))
        throw new AssertionError("R2.8 — a switched-off channel still wrote to the file");
}

// R2.7 — When the diagnostic destination is released, the BC shall flush what it holds and close
// it.
void flushesAndClosesOnRelease(Path destination) throws Exception {
    Log.agent("the last words of the run");
    LogSink.releaseSink();

    var logged = Files.readString(destination);
    if (!logged.contains("the last words of the run"))
        throw new AssertionError("R2.7 — output was lost instead of flushed, file holds: " + logged);

    // released means released: routable output returns to the console
    var size = Files.size(destination);
    var printed = capturing(() -> Log.agent("after the release"));
    if (!printed.contains("after the release"))
        throw new AssertionError("R2.7 — routable output did not return to the console: " + printed);
    if (Files.size(destination) != size)
        throw new AssertionError("R2.7 — the file was still being written to after release");
}

// R2.2 — Where diagnostics are directed to both, the BC shall write every routable channel to the
// file and to the console.
void writesToBothWhenAsked(Path home) throws Exception {
    System.setProperty(LogSink.SINK, "both");
    ZCfg.loadBaseConfig(APP);

    var destination = LogSink.directDiagnostics("both-agent")
            .orElseThrow(() -> new AssertionError("R2.2 — a both sink did not open a file"));
    var printed = capturing(() -> Log.agent("a line for each destination"));

    if (!printed.contains("a line for each destination"))
        throw new AssertionError("R2.2 — the console missed the line: " + printed);
    if (!Files.readString(destination).contains("a line for each destination"))
        throw new AssertionError("R2.2 — the file missed the line");
    LogSink.releaseSink();
}

// R2.6 — If a diagnostic file cannot be opened, then the BC shall report the failure, present the
// routable channels on the console, and leave the run unaffected.
void reportsAFileItCannotOpen(Path home) throws Exception {
    System.setProperty(LogSink.SINK, "file");
    ZCfg.loadBaseConfig(APP);

    var blocked = home.resolve("." + APP).resolve("blocked-agent");
    Files.createDirectories(blocked);
    Files.createFile(blocked.resolve("logs"));

    var printed = capturing(() -> {
        if (LogSink.directDiagnostics("blocked-agent").isPresent())
            throw new AssertionError("R2.6 — claimed to open a log at an unusable destination");
        Log.agent("the run carries on");
    });

    if (!printed.contains("could not open a log"))
        throw new AssertionError("R2.6 — the failure was not reported: " + printed);
    if (!printed.contains("the run carries on"))
        throw new AssertionError("R2.6 — routable output did not fall back to the console: " + printed);
}

String capturing(Runnable emitting) {
    console.reset();
    System.setOut(new PrintStream(console, true, StandardCharsets.UTF_8));
    try {
        emitting.run();
    } finally {
        System.setOut(original);
    }
    return console.toString(StandardCharsets.UTF_8);
}
