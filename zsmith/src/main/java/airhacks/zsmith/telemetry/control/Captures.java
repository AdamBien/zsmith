package airhacks.zsmith.telemetry.control;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.logging.control.Log;
import airhacks.zsmith.telemetry.boundary.EventCapture;

/// The one recording this JVM may be writing, and the rules for when it may start.
///
/// Synchronized rather than atomic: starting is a check followed by several configuration steps
/// before anything is running, and two agents beginning their first run at the same moment would
/// otherwise both get past the check.
public class Captures {

    static final String RECORDINGS_DIRECTORY = "recordings";

    /// The stock JFR profile. It carries the JVM's own events too, which cost about 400KB of
    /// startup noise per run — measured against the leaner profile the difference was under 60KB,
    /// so the fuller one is effectively free.
    static final String SETTINGS = "profile";

    static final long MAX_SIZE = 100L * 1024 * 1024;

    /// Guarded by this class's monitor.
    private static Recording current;

    private Captures() {
    }

    public static synchronized Optional<Path> start(String agentName) {
        if (!ZCfg.bool(EventCapture.ENABLED, false)) {
            return Optional.empty();
        }
        if (agentName == null || agentName.isBlank()) {
            Log.warning("event capture needs an agent name to file the recording under");
            return Optional.empty();
        }
        if (alreadyWritingToAFile()) {
            return Optional.empty();
        }
        try {
            var destination = destinationFor(agentName);
            Files.createDirectories(destination.getParent());
            var recording = new Recording(Configuration.getConfiguration(SETTINGS));
            recording.setName("zsmith-" + agentName);
            recording.setDestination(destination);
            recording.setDumpOnExit(true);
            recording.setMaxSize(MAX_SIZE);
            recording.start();
            current = recording;
            Log.agent("capturing events to " + destination);
            return Optional.of(destination);
        } catch (Exception e) {
            Log.warning("could not capture events for " + agentName + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public static synchronized Optional<Path> stop() {
        if (current == null) {
            return Optional.empty();
        }
        var recording = current;
        current = null;
        var destination = recording.getDestination();
        try {
            recording.stop();
            return Optional.ofNullable(destination);
        } catch (RuntimeException e) {
            Log.warning("could not write the capture to " + destination + ": " + e.getMessage());
            return Optional.empty();
        } finally {
            recording.close();
        }
    }

    /// A live observation registers as a recording too, but streams to a consumer instead of a
    /// file and answers no destination — suppressing capture for one would mean that watching a
    /// run stops it from being kept.
    static boolean alreadyWritingToAFile() {
        return FlightRecorder.getFlightRecorder().getRecordings().stream()
                .anyMatch(recording -> recording.getDestination() != null);
    }

    /// Named by process, because two runs of one agent overlap routinely and the second must not
    /// land on the first.
    static Path destinationFor(String agentName) {
        return ZCfg.agentDirectory(agentName)
                .resolve(RECORDINGS_DIRECTORY)
                .resolve("%s-%d.jfr".formatted(agentName, ProcessHandle.current().pid()));
    }
}
