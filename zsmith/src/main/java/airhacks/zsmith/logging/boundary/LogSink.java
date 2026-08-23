package airhacks.zsmith.logging.boundary;

import java.nio.file.Path;
import java.util.Optional;

import airhacks.zsmith.logging.control.Diagnostics;

/// Where an agent's diagnostics go, and what stays on the console whatever that answer is.
///
/// What the agent asks and answers is its user interface: a question it blocks on has to be
/// visible to the person expected to answer it, so those channels are never redirected. Failures
/// and the progress of a run stay with them. Everything else — turns, tool calls, token counts,
/// wire payloads — is reporting about the run rather than part of it, and can be sent to a file
/// so it stops competing with the conversation for the same terminal.
public interface LogSink {

    /// Console unless asked otherwise, so an upgrade never silently moves a user's output.
    String SINK = "log.sink";

    /// `direct-diagnostics` — send the routable channels of a named agent somewhere.
    ///
    /// Answers the file they are being written to, or nothing when they stay on the console —
    /// because that is what was configured, or because the file could not be opened.
    static Optional<Path> directDiagnostics(String agentName) {
        return Diagnostics.direct(agentName);
    }

    /// `release-sink` — flush what is held and close it, returning the routable channels to the
    /// console.
    static void releaseSink() {
        Diagnostics.release();
    }
}
