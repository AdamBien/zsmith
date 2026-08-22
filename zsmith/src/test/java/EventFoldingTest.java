import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jfr.Recording;

import airhacks.zsmith.claude.entity.ClaudeAPICallEvent;
import airhacks.zsmith.openai.entity.OpenAIAPICallEvent;
import airhacks.zsmith.telemetry.boundary.EventLog;
import airhacks.zsmith.telemetry.entity.RunReport;
import airhacks.zsmith.tools.entity.ToolInvocationEvent;

/// Traces telemetry spec R1.3, R1.4, R2.4 and R2.5 — see
/// src/main/java/airhacks/zsmith/telemetry/package-info.java
///
/// Emits the events directly instead of driving an agent: these statements are about what the
/// fold does with an event, and routing them through a stubbed LLM would only add a way for the
/// test to fail for reasons the requirement says nothing about.

void main() throws Exception {
    var recording = Files.createTempFile("zsmith-folding", ".jfr");
    try {
        var reports = foldEmittedEvents(recording);
        excludesEventsWithoutARun(reports);
        foldsAbsentFieldsAsZero(reports);
        countsARefusedToolAsCallAndFailure(reports);
        countsARepeatAttemptAsRetry(reports);
    } finally {
        Files.deleteIfExists(recording);
    }
}

java.util.Map<String, RunReport> foldEmittedEvents(Path file) throws Exception {
    try (var recorded = new Recording()) {
        recorded.enable(ToolInvocationEvent.NAME);
        recorded.enable(ClaudeAPICallEvent.NAME);
        recorded.enable(OpenAIAPICallEvent.NAME);
        recorded.start();

        toolInvocation("", "orphan", "success", "");
        toolInvocation("r24", "detonate", "denied", "");
        claudeCall("r25", 2);
        openAICall("r14", 40, 7);

        recorded.stop();
        recorded.dump(file);
    }
    return EventLog.replay(file);
}

// R1.3 — If an event carries no run identifier, then the BC shall exclude it from every report.
void excludesEventsWithoutARun(java.util.Map<String, RunReport> reports) {
    if (reports.containsKey(""))
        throw new AssertionError("R1.3 — an event outside any run must not open a report, got: " + reports.keySet());
    if (reports.size() != 3)
        throw new AssertionError("R1.3 — expected only the three identified runs but got: " + reports.keySet());
}

// R1.4 — If a recorded event omits a field a report draws on, then the BC shall fold that field
// as zero rather than fail the replay.
void foldsAbsentFieldsAsZero(java.util.Map<String, RunReport> reports) {
    // an OpenAI call event carries no cache counts at all — the fold reads them regardless
    var tokens = report(reports, "r14").tokens();
    if (tokens.input() != 40 || tokens.output() != 7)
        throw new AssertionError("R1.4 — the fields that are present must still fold, got: " + tokens);
    if (tokens.cacheRead() != 0 || tokens.cacheCreation() != 0)
        throw new AssertionError("R1.4 — absent fields must fold as zero, got: " + tokens);
}

// R2.4 — When a tool was refused before it ran, the BC shall count it as both a call and a
// failure.
void countsARefusedToolAsCallAndFailure(java.util.Map<String, RunReport> reports) {
    var report = report(reports, "r24");
    if (report.toolCalls() != 1)
        throw new AssertionError("R2.4 — a refused tool still spent a round trip, got: " + report.summary());
    if (report.toolFailures() != 1)
        throw new AssertionError("R2.4 — a refused tool is a failure, got: " + report.summary());
    // R2.3 keys it by the refusal outcome, since it never got as far as throwing
    if (!java.util.Map.of("denied", 1).equals(report.failures()))
        throw new AssertionError("R2.4 — expected the refusal outcome as the key, got: " + report.summary());
}

// R2.5 — When an API call was a repeat attempt, the BC shall count it as a retry.
void countsARepeatAttemptAsRetry(java.util.Map<String, RunReport> reports) {
    var report = report(reports, "r25");
    if (report.apiCalls() != 1)
        throw new AssertionError("R2.5 — a repeat attempt is still an API call, got: " + report.summary());
    if (report.retries() != 1)
        throw new AssertionError("R2.5 — expected the second attempt counted as a retry, got: " + report.summary());
}

RunReport report(java.util.Map<String, RunReport> reports, String runId) {
    var report = reports.get(runId);
    if (report == null)
        throw new AssertionError("expected a report for " + runId + " but got: " + reports.keySet());
    return report;
}

void toolInvocation(String runId, String toolName, String outcome, String errorType) {
    var event = new ToolInvocationEvent();
    event.begin();
    event.agentName = "folding-agent";
    event.runId = runId;
    event.iteration = 0;
    event.toolUseId = "toolu_" + toolName;
    event.toolName = toolName;
    event.outcome = outcome;
    event.errorType = errorType;
    event.resultSize = 0;
    event.commit();
}

void claudeCall(String runId, int attempt) {
    var event = new ClaudeAPICallEvent();
    event.begin();
    event.runId = runId;
    event.iteration = 0;
    event.model = "claude-opus-4-8";
    event.attempt = attempt;
    event.statusCode = 200;
    event.stopReason = "end_turn";
    event.inputTokens = 1;
    event.outputTokens = 1;
    event.cacheReadTokens = 0;
    event.cacheCreationTokens = 0;
    event.commit();
}

void openAICall(String runId, int inputTokens, int outputTokens) {
    var event = new OpenAIAPICallEvent();
    event.begin();
    event.runId = runId;
    event.iteration = 0;
    event.model = "gpt-test";
    event.statusCode = 200;
    event.stopReason = "stop";
    event.inputTokens = inputTokens;
    event.outputTokens = outputTokens;
    event.commit();
}
