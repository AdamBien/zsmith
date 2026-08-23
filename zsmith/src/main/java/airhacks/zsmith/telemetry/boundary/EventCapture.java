package airhacks.zsmith.telemetry.boundary;

import java.nio.file.Path;
import java.util.Optional;

import airhacks.zsmith.telemetry.control.Captures;

/// Writes this JVM's events to a file, so a run can be scored after it is over.
///
/// The counterpart of [EventLog#replay]: that reads a recording, this produces one. Until now the
/// only way to get a file was a launcher flag written into each agent script by hand — which no
/// configuration could switch on, and which silently stops travelling the moment the script is
/// copied. A capture asked for in code is reachable the same way transcripts are.
///
/// Capture is JVM-wide, not per agent: a recording observes the whole process. The agent name only
/// decides where the file lands and what it is called.
public interface EventCapture {

    /// Off by default: a capture is the whole run on disk, which is the user's call and not
    /// something an upgrade starts doing.
    String ENABLED = "jfr.enabled";

    /// `record-events` — begin capturing under the given agent's name.
    ///
    /// Answers where the capture is being written, or nothing at all when none was started —
    /// because capture is switched off, because this JVM is already writing one, or because the
    /// destination could not be opened. A run is never held up by its own auditing.
    static Optional<Path> recordEvents(String agentName) {
        return Captures.start(agentName);
    }

    /// `stop-recording` — write the capture out and release it, answering where it landed.
    static Optional<Path> stopRecording() {
        return Captures.stop();
    }
}
