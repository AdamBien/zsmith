import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jdk.jfr.Recording;

import airhacks.zsmith.agent.entity.AgentTurnEvent;
import airhacks.zsmith.claude.entity.ClaudeAPICallEvent;
import airhacks.zsmith.diagnostics.boundary.RunDiagnostics;
import airhacks.zsmith.diagnostics.entity.Finding;
import airhacks.zsmith.tools.entity.ToolInvocationEvent;

/// Traces diagnostics spec R1.1, R1.4 and R1.5 — see
/// src/main/java/airhacks/zsmith/diagnostics/package-info.java
///
/// Scores a written recording rather than hand-built timelines: the rules themselves are stated in
/// DiagnosticsTest, and what is left to check is that a real `.jfr` folds into the shape they
/// expect — that runs stay apart, that a field no event carries reads as zero, and that an event
/// belonging to no run opens no report.

void main() throws Exception {
    var recording = Files.createTempFile("zsmith-diagnosis", ".jfr");
    try {
        var findings = RunDiagnostics.diagnose(write(recording));
        keepsRunsApart(findings);
        foldsAbsentFieldsAsZero(findings);
        excludesEventsWithoutARun(findings);
        reportsTheSameFindingsAsText(recording, findings);
    } finally {
        Files.deleteIfExists(recording);
    }
}

Path write(Path file) throws Exception {
    try (var recorded = new Recording()) {
        recorded.enable(AgentTurnEvent.NAME);
        recorded.enable(ToolInvocationEvent.NAME);
        recorded.enable(ClaudeAPICallEvent.NAME);
        recorded.start();

        // a parent that serialized its tool use, and a child it delegated to that batched
        turn("parent", "", 0, 7, 1);
        turn("child", "parent", 1, 8, 8);
        claudeCall("parent");
        claudeCall("child");
        toolInvocation("parent", "read_any_file", 66_798);
        toolInvocation("", "orphan", 0);

        recorded.stop();
        recorded.dump(file);
    }
    return file;
}

// R1.1 — one set of findings per run identifier, never a verdict spanning both.
void keepsRunsApart(List<Finding> findings) {
    var runs = findings.stream().map(Finding::runId).distinct().toList();
    if (!List.of("parent", "child").equals(runs))
        throw new AssertionError("R1.1 — expected the parent's findings before the child's, got: " + runs);
    var batching = findings.stream().filter(finding -> "batching".equals(finding.kind())).toList();
    if (batching.size() != 2)
        throw new AssertionError("R1.1 — each run is judged on its own tool use, got: " + batching);
    if (batching.getFirst().severity() != Finding.Severity.NOTE
            || batching.getLast().severity() != Finding.Severity.PASS)
        throw new AssertionError("R1.1 — 1 of 7 batched and 8 of 8 are not the same verdict: " + batching);
}

// R1.4 — a Claude call event carries no `agentName`, and a turn event no token counts; every rule
// still runs rather than failing the read.
void foldsAbsentFieldsAsZero(List<Finding> findings) {
    if (findings.stream().noneMatch(finding -> "retries".equals(finding.kind())))
        throw new AssertionError("R1.4 — the diagnosis must survive fields an event does not carry: " + findings);
}

// R1.5 — an event outside any run belongs to no run and opens no findings.
void excludesEventsWithoutARun(List<Finding> findings) {
    if (findings.stream().anyMatch(finding -> finding.runId().isBlank()))
        throw new AssertionError("R1.5 — an orphan event must not open a report: " + findings);
}

// The text report says exactly what the findings say, grouped by run.
void reportsTheSameFindingsAsText(Path recording, List<Finding> findings) {
    var report = RunDiagnostics.report(recording);
    if (!report.contains("run parent") || !report.contains("run child"))
        throw new AssertionError("expected both runs named in the report, got:\n" + report);
    findings.forEach(finding -> {
        if (!report.contains(finding.summary()))
            throw new AssertionError("expected " + finding.kind() + " in the report, got:\n" + report);
    });
}

void turn(String runId, String parentRunId, int depth, int toolUseCount, int parallelToolCount) {
    var event = new AgentTurnEvent();
    event.begin();
    event.agentName = runId + "-agent";
    event.runId = runId;
    event.parentRunId = parentRunId;
    event.depth = depth;
    event.iteration = 0;
    event.stopReason = "end_turn";
    event.toolUseCount = toolUseCount;
    event.parallelToolCount = parallelToolCount;
    event.sequentialToolCount = toolUseCount - parallelToolCount;
    event.terminal = true;
    event.commit();
}

void claudeCall(String runId) {
    var event = new ClaudeAPICallEvent();
    event.begin();
    event.runId = runId;
    event.iteration = 0;
    event.model = "claude-opus-4-8";
    event.attempt = 1;
    event.statusCode = 200;
    event.stopReason = "end_turn";
    event.inputTokens = 1;
    event.outputTokens = 1;
    event.cacheReadTokens = 9_327;
    event.cacheCreationTokens = 0;
    event.commit();
}

void toolInvocation(String runId, String toolName, int resultSize) {
    var event = new ToolInvocationEvent();
    event.begin();
    event.agentName = "diagnosed-agent";
    event.runId = runId;
    event.iteration = 0;
    event.toolUseId = "toolu_" + toolName;
    event.toolName = toolName;
    event.outcome = "success";
    event.errorType = "";
    event.resultSize = resultSize;
    event.commit();
}
