package airhacks.zsmith.transcripts.boundary;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.htmldb.boundary.HtmlStore;
import airhacks.zsmith.logging.control.Log;
import airhacks.zsmith.transcripts.entity.Transcript;

/// A `transcripts` table in the agent's own database, alongside its memories and its
/// improvement backlog. Each record is keyed by the `runId` the JFR events of that run
/// carry, so a run singled out by telemetry can be read in full.
///
/// Unlike [airhacks.zsmith.improvements.boundary.ImprovementLog] this never loads the table
/// into memory: transcripts are written once and read by the record, and an agent with a
/// long history would otherwise pay for all of them on every construction.
public record TranscriptLog(HtmlStore store) {

    static final String TRANSCRIPTS_TABLE = "transcripts";

    /// Off by default: a transcript is the whole conversation on disk, which is the user's
    /// call to make and not a side effect of upgrading.
    public static final String ENABLED = "transcripts.enabled";

    public TranscriptLog(Path databaseRoot) {
        this(new HtmlStore(databaseRoot));
    }

    public static TranscriptLog forAgent(String agentName) {
        return new TranscriptLog(ZCfg.agentDatabase(agentName));
    }

    public static boolean enabled() {
        return ZCfg.bool(ENABLED, false);
    }

    /// Answers false when the transcript could not be written, so a full disk degrades the
    /// claim check rather than the chat that produced it.
    public boolean save(Transcript transcript) {
        try {
            this.store.put(TRANSCRIPTS_TABLE, transcript.runId(), transcript.toFields());
            return true;
        } catch (IllegalArgumentException | UncheckedIOException e) {
            Log.warning("could not store transcript " + transcript.runId() + ": " + e.getMessage());
            return false;
        }
    }

    public Optional<Transcript> read(String runId) {
        try {
            return this.store.get(TRANSCRIPTS_TABLE, runId).map(entry -> Transcript.fromFields(entry.fields()));
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException e) {
            Log.warning("skipping unreadable transcript " + runId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    public List<String> runIds() {
        return this.store.keys(TRANSCRIPTS_TABLE);
    }
}
