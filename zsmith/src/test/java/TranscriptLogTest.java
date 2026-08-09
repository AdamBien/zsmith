import java.nio.file.Files;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.transcripts.boundary.TranscriptLog;
import airhacks.zsmith.transcripts.entity.Transcript;

void main() throws Exception {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());

    storesAndReadsBackByRunId();
    refusesATranscriptWithoutARun();
    isOffUnlessAskedFor();
}

/// The claim check only works if the key the events carry is the key the payload is under.
void storesAndReadsBackByRunId() throws Exception {
    var databaseRoot = Files.createTempDirectory("transcripts-roundtrip");
    var log = new TranscriptLog(databaseRoot);
    var runId = "3f2b1a9c-0000-4000-8000-000000000001";
    var conversation = """
            [{"role":"user","content":"summarize <this> & that"}]""";

    assert log.save(Transcript.of(runId, "scribe", "end_turn", 3, conversation)) : "the transcript should be stored";

    var restored = new TranscriptLog(databaseRoot).read(runId).orElseThrow();
    assert runId.equals(restored.runId()) : "run id should survive, got: " + restored.runId();
    assert "scribe".equals(restored.agent()) : "agent should survive, got: " + restored.agent();
    assert "end_turn".equals(restored.outcome()) : "outcome should survive, got: " + restored.outcome();
    assert restored.turns() == 3 : "turns should survive, got: " + restored.turns();
    // the payload is markup-bearing JSON and the store is XHTML — it has to come back verbatim
    assert conversation.equals(restored.conversation()) : "conversation should survive, got: " + restored.conversation();

    assert log.runIds().contains(runId) : "the run should be listed, got: " + log.runIds();
    assert Files.exists(databaseRoot.resolve("transcripts").resolve("index.html")) : "table index should be generated";
}

/// Without a run id there is nothing to join the transcript to, which makes it unfindable
/// rather than merely untidy.
void refusesATranscriptWithoutARun() {
    try {
        Transcript.of("", "scribe", "end_turn", 1, "[]");
        throw new AssertionError("a transcript without a run id should be refused");
    } catch (IllegalArgumentException expected) {
        assert expected.getMessage().contains("runId") : "unexpected message: " + expected.getMessage();
    }
}

/// Writing every conversation to disk is opt-in — an upgrade must not start doing it.
void isOffUnlessAskedFor() {
    assert !TranscriptLog.enabled() : "transcripts should be off unless " + TranscriptLog.ENABLED + " says otherwise";
}
