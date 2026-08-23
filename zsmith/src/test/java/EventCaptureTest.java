import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

import airhacks.zsmith.agent.entity.AgentTurnEvent;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.telemetry.boundary.EventCapture;
import airhacks.zsmith.telemetry.boundary.EventLog;

/// Traces telemetry spec R4.1–R4.8 — see src/main/java/airhacks/zsmith/telemetry/package-info.java
///
/// Capture is JVM-wide and there is only ever one of it, so the order here is deliberate: the
/// switched-off case has to run before anything turns it on, and the collision case needs a
/// foreign recording already in flight.

static final String APP = "zsmith-capture-" + ProcessHandle.current().pid();
static final String AGENT = "captured-agent";

void main() throws Exception {
    var home = Files.createTempDirectory("zsmith-capture-home");
    System.setProperty("user.home", home.toString());

    capturesNothingWhileSwitchedOff();
    refusesToJoinAnExistingFileRecording();
    enableCapture();
    capturesToTheAgentsOwnDirectory(home);
}

// R4.2 — Where event capture is not enabled, the BC shall capture nothing when a capture is
// requested.
void capturesNothingWhileSwitchedOff() {
    ZCfg.loadBaseConfig(APP);

    var started = EventCapture.recordEvents(AGENT);
    if (started.isPresent())
        throw new AssertionError("R4.2 — a disabled capture started anyway at " + started.get());
    if (writingRecordings() != 0)
        throw new AssertionError("R4.2 — a disabled capture left a recording running");
}

// R4.5 — If this JVM is already capturing events to a file, then the BC shall not begin a second
// capture.
void refusesToJoinAnExistingFileRecording() throws Exception {
    System.setProperty(EventCapture.ENABLED, "true");
    ZCfg.loadBaseConfig(APP);

    var foreign = Files.createTempFile("foreign-recording", ".jfr");
    try (var flagLike = new Recording()) {
        flagLike.setDestination(foreign);
        flagLike.start();

        var started = EventCapture.recordEvents(AGENT);
        if (started.isPresent())
            throw new AssertionError("R4.5 — a second capture began while one was already writing to "
                    + foreign + ", at " + started.get());
        if (writingRecordings() != 1)
            throw new AssertionError("R4.5 — expected exactly one file recording but found " + writingRecordings());
        flagLike.stop();
    }
    Files.deleteIfExists(foreign);

    // and once nothing is writing, capture is free to start again
    System.clearProperty(EventCapture.ENABLED);
}

void enableCapture() {
    System.setProperty(EventCapture.ENABLED, "true");
    ZCfg.loadBaseConfig(APP);
}

// R4.1 — Where event capture is enabled, the BC shall capture this JVM's events to a file when a
// capture is requested.
// R4.3 — When a capture is requested for a named agent, the BC shall write it under that agent's
// own directory.
// R4.4 — When a capture is written, the BC shall name it so that concurrent runs do not overwrite
// one another.
// R4.6 — When the JVM exits while capturing, the BC shall write the capture to its destination.
// R4.7 — When a capture is stopped, the BC shall write it to its destination and release it.
// R4.8 — If a capture cannot be started or written, then the BC shall report the failure and leave
// the run unaffected.
void capturesToTheAgentsOwnDirectory(Path home) throws Exception {
    var destination = EventCapture.recordEvents(AGENT)
            .orElseThrow(() -> new AssertionError("R4.1 — an enabled capture did not start"));

    // R4.3 — under this agent's directory, not some other agent's and not a shared one
    var agentDirectory = home.resolve("." + APP).resolve(AGENT);
    if (!destination.startsWith(agentDirectory))
        throw new AssertionError("R4.3 — expected the capture under " + agentDirectory + " but got " + destination);

    // R4.4 — the process is in the name, so a concurrent run of the same agent cannot collide
    var pid = String.valueOf(ProcessHandle.current().pid());
    if (!destination.getFileName().toString().contains(pid))
        throw new AssertionError("R4.4 — expected the process in the name but got " + destination.getFileName());

    // R4.6 — the running capture is set to survive an exit, which is how every real agent run ends
    var running = FlightRecorder.getFlightRecorder().getRecordings().stream()
            .filter(r -> destination.equals(r.getDestination()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("R4.6 — the capture is not registered with the recorder"));
    if (!running.getDumpOnExit())
        throw new AssertionError("R4.6 — the capture would be lost on exit");

    // R4.5 — asking again while this one runs changes nothing
    if (EventCapture.recordEvents(AGENT).isPresent())
        throw new AssertionError("R4.5 — a second capture began while ours was already writing");

    emitTurn("capture-run");

    // R4.7 — stopping writes the file out and hands back where it landed
    var written = EventCapture.stopRecording()
            .orElseThrow(() -> new AssertionError("R4.7 — stopping answered no destination"));
    if (!written.equals(destination))
        throw new AssertionError("R4.7 — stopped at " + written + " but started at " + destination);
    if (!Files.exists(written) || Files.size(written) == 0)
        throw new AssertionError("R4.7 — no capture was written to " + written);
    if (writingRecordings() != 0)
        throw new AssertionError("R4.7 — the capture was not released after stopping");

    // the file is a real recording: the event emitted while capturing folds back out of it
    var reports = EventLog.replay(written);
    if (!reports.containsKey("capture-run"))
        throw new AssertionError("R4.1 — the capture holds no events, only: " + reports.keySet());
    if (reports.get("capture-run").turns() != 1)
        throw new AssertionError("R4.1 — expected the captured turn, got: " + reports.get("capture-run").summary());

    // R4.8 — a destination that cannot be created is reported, not thrown
    var blocked = home.resolve("." + APP).resolve("blocked-agent");
    Files.createDirectories(blocked);
    Files.createFile(blocked.resolve("recordings"));
    if (EventCapture.recordEvents("blocked-agent").isPresent())
        throw new AssertionError("R4.8 — a capture claimed to start with an unusable destination");
    if (writingRecordings() != 0)
        throw new AssertionError("R4.8 — a failed capture left a recording running");
}

void emitTurn(String runId) {
    var event = new AgentTurnEvent();
    event.begin();
    event.agentName = AGENT;
    event.runId = runId;
    event.parentRunId = "";
    event.depth = 0;
    event.iteration = 0;
    event.stopReason = "end_turn";
    event.terminal = true;
    event.commit();
}

long writingRecordings() {
    return FlightRecorder.getFlightRecorder().getRecordings().stream()
            .filter(recording -> recording.getDestination() != null)
            .count();
}
