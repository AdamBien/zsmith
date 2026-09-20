import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.episodicmemory.boundary.EpisodicMemoryStore;
import airhacks.zsmith.episodicmemory.entity.Episode;
import airhacks.zsmith.episodicmemory.entity.MemoryAccessEvent;
import airhacks.zsmith.episodicmemory.entity.MemoryType;
import airhacks.zsmith.telemetry.boundary.EventCapture;

/// Traces episodicmemory spec R9.1–R9.2 — see
/// src/main/java/airhacks/zsmith/episodicmemory/package-info.java
///
/// Asserted against a written recording rather than a live stream: a stream sees only
/// what is flushed while it runs, and the accesses under test are over in microseconds.

void main() throws Exception {
    var home = Files.createTempDirectory("zsmith-memory-access");
    System.setProperty("user.home", home.toString());
    System.setProperty(EventCapture.ENABLED, "true");
    // the capture switch is read from the configuration, which snapshots the system
    // properties as it loads — so both are set before the cache exists
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    try {
        reportsWhatAnAccessCost();
        reportsAFailedAccess();
    } finally {
        System.clearProperty(EventCapture.ENABLED);
    }
}

// R9.1 — When memories are loaded or kept, the BC shall emit an event carrying the run
// it happened in, the operation, the number of memories held, the payload size and the
// outcome.
void reportsWhatAnAccessCost() throws Exception {
    var databaseRoot = Files.createTempDirectory("memory-access-success");

    var recording = record(() -> {
        new EpisodicMemoryStore(databaseRoot).store(Episode.of("zb builds the project", MemoryType.project));
        // a second store over the same folder loads what the first one wrote
        new EpisodicMemoryStore(databaseRoot);
    });

    var accesses = memoryAccessesIn(recording);
    var saved = accessTo(accesses, "save");
    var loads = accessesTo(accesses, "load");

    assert "episodic".equals(saved.getString("store")) : "R9.1 — the event names the store it happened in";
    assert "success".equals(saved.getString("outcome")) : "R9.1 — a write that worked reports success";
    assert saved.getInt("episodeCount") >= 1 : "R9.1 — the event carries how many memories are held";
    assert saved.getInt("payloadSize") > 0 : "R9.1 — the event carries what the write cost";
    assert saved.getEventType().getField("runId") != null : "R9.1 — the event carries the run it happened in";
    assert loads.stream().allMatch(load -> "success".equals(load.getString("outcome")))
            : "R9.1 — a load that worked reports success";
    // the first store opens an empty folder, the second one opens what it wrote
    assert loads.stream().anyMatch(load -> load.getInt("episodeCount") >= 1)
            : "R9.1 — a load reports how many memories it found";
}

// R9.2 — If an access fails, then the BC shall report the failure as that event's
// outcome.
void reportsAFailedAccess() throws Exception {
    var databaseRoot = Files.createTempDirectory("memory-access-failure");
    // a regular file where the table has to be a folder: the write has nowhere to land
    Files.writeString(databaseRoot.resolve("episodes"), "not a table");

    var recording = record(() -> {
        var store = new EpisodicMemoryStore(databaseRoot);
        try {
            store.store(Episode.of("a fact that cannot land", MemoryType.project));
            throw new AssertionError("R9.2 — the write had nowhere to go and should have failed");
        } catch (UncheckedIOException expected) {
            // the failure R9.2 asks to be reported
        }
    });

    var failed = accessTo(memoryAccessesIn(recording), "save");

    assert "io_error".equals(failed.getString("outcome"))
            : "R9.2 — a failed write must report its failure, got: " + failed.getString("outcome");
}

Path record(Runnable work) {
    EventCapture.recordEvents("memory-access")
            .orElseThrow(() -> new AssertionError("could not capture events to observe memory access"));
    try {
        work.run();
    } finally {
        var written = EventCapture.stopRecording();
        if (written.isEmpty()) {
            throw new AssertionError("the capture was started but never landed on disk");
        }
        this.recording = written.get();
    }
    return this.recording;
}

Path recording;

RecordedEvent accessTo(List<RecordedEvent> accesses, String operation) {
    var matching = accessesTo(accesses, operation);
    if (matching.isEmpty()) {
        throw new AssertionError("no memory access event for '%s', got: %s"
                .formatted(operation, accesses.stream().map(event -> event.getString("operation")).toList()));
    }
    return matching.getFirst();
}

List<RecordedEvent> accessesTo(List<RecordedEvent> accesses, String operation) {
    return accesses.stream()
            .filter(event -> operation.equals(event.getString("operation")))
            .toList();
}

List<RecordedEvent> memoryAccessesIn(Path recording) throws Exception {
    var accesses = new ArrayList<RecordedEvent>();
    try (var file = new RecordingFile(recording)) {
        while (file.hasMoreEvents()) {
            var event = file.readEvent();
            if (MemoryAccessEvent.NAME.equals(event.getEventType().getName())) {
                accesses.add(event);
            }
        }
    }
    return accesses;
}
