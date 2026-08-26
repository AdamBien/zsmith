import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import airhacks.zsmith.diagnostics.control.Diagnostics;
import airhacks.zsmith.diagnostics.entity.Call;
import airhacks.zsmith.diagnostics.entity.Finding;
import airhacks.zsmith.diagnostics.entity.Timeline;
import airhacks.zsmith.diagnostics.entity.ToolCall;
import airhacks.zsmith.telemetry.entity.RunReport;
import airhacks.zsmith.telemetry.entity.TokenUsage;

/// Traces diagnostics spec R1.2, R2.1–R2.8 — see
/// src/main/java/airhacks/zsmith/diagnostics/package-info.java
///
/// Drives the rules directly instead of through a recording: an expiry is a sixteen-minute gap
/// between two calls, and no test can wait for one. Handing the rules a timeline built by hand is
/// the only way to state R2.1 and R2.3 at all — that the recording carrying such a gap folds into
/// this shape is what RunDiagnosticsIT is for.

static final Instant NOON = Instant.parse("2026-08-26T12:00:00Z");

/// Long enough after the first call *ends* that the prefix cannot have survived — the gap is
/// measured from the previous call finishing, which is when the conversation actually went idle.
static final Instant AFTER_THE_CACHE_DIED = NOON.plusSeconds(2).plus(Duration.ofMinutes(16));

void main() {
    reportsAReCreatedPrefix();
    neverReportsAFirstCallAsExpired();
    reportsAnIdleGap();
    reportsALargeResultWithTheTurnsThatCarriedIt();
    ignoresALargeResultNoTurnFollowed();
    reportsSerializedToolCalls();
    passesAHealthyRun();
    reportsRetriesAndFailures();
    reportsAnIncompleteRun();
    reportsADelegatedRunAgainstItself();
}

// R2.1 — a call that read nothing from cache and wrote a significant prefix, and was not the
// run's first, is the prefix re-created at write price.
void reportsAReCreatedPrefix() {
    var findings = diagnose(
            timeline("parent", 0, calls(
                    call(0, NOON, 20_000, 0),
                    call(1, AFTER_THE_CACHE_DIED, 0, 68_789)),
                    List.of(), 0, 0),
            report("parent", 2, 2));
    var expiry = of("cache-expired", findings);
    if (expiry.severity() != Finding.Severity.WARNING)
        throw new AssertionError("R2.1 — a re-created prefix is avoidable cost: " + expiry);
    if (!expiry.evidence().contains("68789"))
        throw new AssertionError("R2.1 — the finding must carry what it cost (R1.3): " + expiry);
}

// R2.3 — a first call reads nothing from cache by definition; a sub-agent starting cold must not
// look like an expiry. This is the only rule separating the two.
void neverReportsAFirstCallAsExpired() {
    var findings = diagnose(
            timeline("child", 1, calls(
                    call(0, NOON, 0, 68_789),
                    call(1, NOON.plusSeconds(30), 1_349, 22_272)),
                    List.of(), 0, 0),
            report("child", 2, 2));
    if (findings.stream().anyMatch(finding -> "cache-expired".equals(finding.kind())))
        throw new AssertionError("R2.3 — a cold start is not an expiry: " + findings);
}

// R2.2 — the gap is the cause, and is reported even though R2.1 reports its effect.
void reportsAnIdleGap() {
    var findings = diagnose(
            timeline("parent", 0, calls(
                    call(0, NOON, 20_000, 0),
                    call(1, AFTER_THE_CACHE_DIED, 0, 68_789)),
                    List.of(), 0, 0),
            report("parent", 2, 2));
    var gap = of("idle-gap", findings);
    if (!gap.evidence().contains("16m 00s"))
        throw new AssertionError("R2.2 — the gap must be measured, not just named: " + gap);
}

// R2.4 — a large result costs its size times the turns that carried it, so both are reported.
void reportsALargeResultWithTheTurnsThatCarriedIt() {
    var findings = diagnose(
            timeline("parent", 0, calls(call(0, NOON, 10, 0)),
                    List.of(new ToolCall("parent", 0, "recall_memory", 54_998)), 0, 0),
            report("parent", 18, 1));
    var oversized = of("oversized-tool-result", findings);
    if (!oversized.evidence().contains("54998") || !oversized.evidence().contains("17"))
        throw new AssertionError("R2.4 — expected the size and the 17 turns that carried it: " + oversized);
}

// R2.4 — a result nothing followed was read once and is not worth reporting.
void ignoresALargeResultNoTurnFollowed() {
    var findings = diagnose(
            timeline("parent", 0, calls(call(0, NOON, 10, 0)),
                    List.of(new ToolCall("parent", 17, "read_any_file", 66_798)), 0, 0),
            report("parent", 18, 1));
    if (findings.stream().anyMatch(finding -> "oversized-tool-result".equals(finding.kind())))
        throw new AssertionError("R2.4 — a result the final turn fetched is carried by nothing: " + findings);
}

// R2.5 — the share of tool calls issued in one turn rather than one at a time.
void reportsSerializedToolCalls() {
    var serial = of("batching", diagnose(
            timeline("parent", 0, calls(call(0, NOON, 10, 0)), List.of(), 23, 4),
            report("parent", 18, 23)));
    if (serial.severity() != Finding.Severity.NOTE || !serial.evidence().contains("4 of 23"))
        throw new AssertionError("R2.5 — expected serialized tool use reported with its share: " + serial);

    var batched = of("batching", diagnose(
            timeline("child", 1, calls(call(0, NOON, 10, 0)), List.of(), 80, 79),
            report("child", 9, 80)));
    if (batched.severity() != Finding.Severity.PASS)
        throw new AssertionError("R2.5 — 79 of 80 batched is healthy: " + batched);
}

// R1.2 — a run that was checked and found healthy says so rather than saying nothing.
void passesAHealthyRun() {
    var findings = diagnose(
            timeline("parent", 0, calls(call(0, NOON, 10, 0)), List.of(), 0, 0),
            report("parent", 3, 2));
    if (findings.stream().noneMatch(finding -> finding.severity() == Finding.Severity.PASS))
        throw new AssertionError("R1.2 — a clean run must be distinguishable from an unchecked one: " + findings);
}

// R2.6 — retries and tool failures, keyed by what went wrong when there were any.
void reportsRetriesAndFailures() {
    var report = new RunReport("parent", "airhacksfm", 0, 4, 3, 2, Map.of("denied", 1, "IOException", 1),
            0, 4, 1, TokenUsage.NONE, true);
    var findings = diagnose(timeline("parent", 0, calls(call(0, NOON, 10, 0)), List.of(), 0, 0), report);
    if (of("retries", findings).severity() != Finding.Severity.WARNING)
        throw new AssertionError("R2.6 — a retried call is a warning: " + findings);
    var failures = of("tool-failures", findings);
    if (!failures.evidence().contains("denied") || !failures.evidence().contains("IOException"))
        throw new AssertionError("R2.6 — failures must be keyed by what went wrong: " + failures);
}

// R2.7 — a run that never reached a terminal turn.
void reportsAnIncompleteRun() {
    var report = new RunReport("parent", "airhacksfm", 0, 40, 0, 0, Map.of(), 0, 40, 0, TokenUsage.NONE, false);
    var findings = diagnose(timeline("parent", 0, calls(call(0, NOON, 10, 0)), List.of(), 0, 0), report);
    if (of("incomplete", findings).severity() != Finding.Severity.WARNING)
        throw new AssertionError("R2.7 — an unterminated run is a warning: " + findings);
}

// R2.8 — a delegated run's cost is reported against the child, never added to the parent.
void reportsADelegatedRunAgainstItself() {
    var child = timeline("child", 1, calls(call(0, NOON, 10, 0)), List.of(), 0, 0);
    var cost = of("subagent-cost", diagnose(child, report("child", 9, 0)));
    if (!"child".equals(cost.runId()))
        throw new AssertionError("R2.8 — the cost belongs to the run that spent it: " + cost);
    if (!cost.evidence().contains("parent"))
        throw new AssertionError("R2.8 — the finding must name who dispatched it: " + cost);

    var parent = timeline("parent", 0, calls(call(0, NOON, 10, 0)), List.of(), 0, 0);
    if (diagnose(parent, report("parent", 18, 0)).stream()
            .anyMatch(finding -> "subagent-cost".equals(finding.kind())))
        throw new AssertionError("R2.8 — a parent must not carry its children's cost");
}

List<Finding> diagnose(Timeline timeline, RunReport report) {
    return Diagnostics.findings(Map.of(timeline.runId(), report), Map.of(timeline.runId(), timeline));
}

Finding of(String kind, List<Finding> findings) {
    return findings.stream()
            .filter(finding -> kind.equals(finding.kind()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected a " + kind + " finding, got: " + findings));
}

Timeline timeline(String runId, int depth, List<Call> calls, List<ToolCall> toolCalls,
        int toolUses, int parallelToolUses) {
    return new Timeline(runId, runId + "-agent", depth == 0 ? "" : "parent", depth,
            calls, toolCalls, toolUses, parallelToolUses);
}

List<Call> calls(Call... calls) {
    return List.of(calls);
}

Call call(int iteration, Instant started, int cacheRead, int cacheCreation) {
    return new Call("run", started, started.plusSeconds(2), iteration, cacheRead, cacheCreation);
}

RunReport report(String runId, int turns, int toolCalls) {
    return new RunReport(runId, runId + "-agent", 0, turns, toolCalls, 0, Map.of(), 0, turns, 0,
            new TokenUsage(10, 20, 30, 40), true);
}
