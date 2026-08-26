package airhacks.zsmith.diagnostics.control;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import airhacks.zsmith.diagnostics.entity.Call;
import airhacks.zsmith.diagnostics.entity.Finding;
import airhacks.zsmith.diagnostics.entity.Timeline;
import airhacks.zsmith.diagnostics.entity.ToolCall;
import airhacks.zsmith.telemetry.entity.RunReport;

/// Turns what a run cost into whether that was avoidable.
///
/// Every rule here is arithmetic over recorded fields, which is the whole design: the numbers that
/// matter — a prefix re-created after an idle stretch, a 65 KB tool result riding seventeen turns —
/// are sums and differences, and a model asked to find them in a log would be guessing at what it
/// could have counted. What a model is good for sits above this: reading the transcript to say why
/// a tool returned 55 KB when two sentences of it were used.
///
/// The rules are stated so that a healthy run says so. A report that only ever lists problems
/// cannot distinguish a clean run from one nobody checked.
public interface Diagnostics {

    /// The prompt cache's time to live. A run whose own calls are spaced further apart than this
    /// pays to re-create the prefix it could have read.
    Duration CACHE_TTL = Duration.ofMinutes(5);

    /// Below this, a re-created prefix is not worth anyone's attention — a few hundred tokens of
    /// cache writing is the ordinary cost of a conversation growing.
    int SIGNIFICANT_CACHE_CREATION = 4_096;

    /// A tool result larger than this is worth naming, because it does not cost once: it is
    /// appended to the conversation and re-sent on every turn that follows.
    int OVERSIZED_RESULT = 16_384;

    /// A large result only matters if turns followed it. One fetched by the final turn is read
    /// once and never carried.
    int CARRIED_TURNS = 2;

    /// Below this many tool calls per turn, the model is asking for work one item at a time that
    /// it could have asked for together — and each extra turn is a whole round trip through the
    /// model, paid for by re-sending the entire conversation.
    double WELL_BATCHED = 1.5;

    /// Below this many tool calls there is nothing to say about batching: a single call cannot be
    /// batched with anything, and a handful spread over a conversation is a workflow rather than a
    /// missed opportunity.
    int BATCHABLE_TOOL_USES = 6;

    static List<Finding> findings(Map<String, RunReport> reports, Map<String, Timeline> timelines) {
        return timelines.values().stream()
                .sorted(Comparator.comparingInt(Timeline::depth).thenComparing(Timeline::runId))
                .flatMap(timeline -> forRun(timeline, reports.get(timeline.runId())).stream())
                .toList();
    }

    static List<Finding> forRun(Timeline timeline, RunReport report) {
        var findings = new ArrayList<Finding>();
        findings.addAll(cacheFindings(timeline));
        findings.addAll(oversizedResults(timeline, report));
        batching(timeline).ifPresent(findings::add);
        if (report != null) {
            findings.add(retries(timeline.runId(), report));
            findings.add(toolFailures(timeline.runId(), report));
            completeness(timeline.runId(), report).ifPresent(findings::add);
        }
        delegation(timeline, report).ifPresent(findings::add);
        findings.sort(Comparator.comparing(Finding::severity));
        return findings;
    }

    /// The expiry rule and the idle rule are the same evidence read twice, and both are reported:
    /// the gap is what happened, the re-created prefix is what it cost. A run's *first* call reads
    /// nothing from cache by definition and is never evidence of either — which is the only thing
    /// separating a real expiry from the four sub-agents in a delegating run starting cold.
    static List<Finding> cacheFindings(Timeline timeline) {
        var findings = new ArrayList<Finding>();
        var calls = timeline.calls();
        for (var index = 1; index < calls.size(); index++) {
            var call = calls.get(index);
            var idle = call.since(calls.get(index - 1));
            if (idle.compareTo(CACHE_TTL) > 0) {
                findings.add(Finding.note(timeline.runId(), "idle-gap",
                        "the run was blocked longer than the prompt cache lives",
                        "%s %s between turn %d and turn %d, cache TTL is %s".formatted(
                                humanized(idle), blamed(timeline, calls.get(index - 1), call),
                                calls.get(index - 1).iteration(), call.iteration(),
                                humanized(CACHE_TTL))));
            }
            if (expired(call)) {
                findings.add(Finding.warning(timeline.runId(), "cache-expired",
                        "the prefix was re-created at write price instead of read from cache",
                        "turn %d read 0 cached tokens and wrote %d after %s %s".formatted(
                                call.iteration(), call.cacheCreation(), humanized(idle),
                                blamed(timeline, calls.get(index - 1), call))));
            }
        }
        return findings;
    }

    /// What held the window open, when the recording says. A tool that ran the length of the gap
    /// names the cause outright — a question nobody answered, or a sub-agent still working, which
    /// are the same gap and not at all the same problem. Falls back to the bare gap when no tool
    /// covers it, because then the run really was doing nothing.
    static String blamed(Timeline timeline, Call previous, Call call) {
        return timeline.blockedOn(previous.ended(), call.started())
                .map(tool -> "waiting on %s".formatted(tool.toolName()))
                .orElse("idle");
    }

    static boolean expired(Call call) {
        return call.readNothingFromCache() && call.cacheCreation() >= SIGNIFICANT_CACHE_CREATION;
    }

    /// Charged by what a result costs over the rest of the run rather than by its own size, since
    /// that is what actually leaves the conversation: bytes multiplied by the turns that carry it.
    static List<Finding> oversizedResults(Timeline timeline, RunReport report) {
        var turns = report == null ? 0 : report.turns();
        return timeline.toolCalls().stream()
                .filter(call -> call.resultSize() >= OVERSIZED_RESULT)
                .filter(call -> call.turnsCarried(turns) >= CARRIED_TURNS)
                .map(call -> Finding.note(timeline.runId(), "oversized-tool-result",
                        "a large tool result rode the conversation for the rest of the run",
                        described(call, turns)))
                .toList();
    }

    static String described(ToolCall call, int turns) {
        return "%s returned %d bytes at turn %d, re-sent on %d further turns".formatted(
                call.toolName(), call.resultSize(), call.iteration(), call.turnsCarried(turns));
    }

    static Optional<Finding> batching(Timeline timeline) {
        if (timeline.toolUses() < BATCHABLE_TOOL_USES) {
            return Optional.empty();
        }
        // ROOT rather than the default: a finding is compared against another run's, and a
        // decimal separator that follows whoever is running the analyzer makes that harder.
        var evidence = String.format(Locale.ROOT, "%d tool calls over %d turns, %.1f per round trip",
                timeline.toolUses(), timeline.turnsWithTools(), timeline.toolsPerTurn());
        if (timeline.toolsPerTurn() >= WELL_BATCHED) {
            return Optional.of(Finding.pass(timeline.runId(), "batching",
                    "tool calls shared their turns rather than costing a round trip each", evidence));
        }
        return Optional.of(Finding.note(timeline.runId(), "batching",
                "tool calls were asked for one at a time, a round trip each", evidence));
    }

    static Finding retries(String runId, RunReport report) {
        if (report.retries() == 0) {
            return Finding.pass(runId, "retries", "every API call succeeded on its first attempt",
                    "%d calls, 0 retries".formatted(report.apiCalls()));
        }
        return Finding.warning(runId, "retries", "API calls were repeated",
                "%d retries across %d calls".formatted(report.retries(), report.apiCalls()));
    }

    static Finding toolFailures(String runId, RunReport report) {
        if (report.toolFailures() == 0) {
            return Finding.pass(runId, "tool-failures", "no tool failed or was refused",
                    "%d tool calls".formatted(report.toolCalls()));
        }
        return Finding.warning(runId, "tool-failures", "tools failed or were refused",
                "%d of %d tool calls, by kind: %s".formatted(
                        report.toolFailures(), report.toolCalls(), report.failures()));
    }

    static Optional<Finding> completeness(String runId, RunReport report) {
        if (!report.incomplete()) {
            return Optional.empty();
        }
        return Optional.of(Finding.warning(runId, "incomplete",
                "the run never reached a terminal turn",
                "%d turns, stopped without ending".formatted(report.turns())));
    }

    /// Reported on the child, never rolled into the parent: a delegated run is its own run, and
    /// adding its spend to the run that dispatched it would leave neither number the cost of
    /// anything.
    static Optional<Finding> delegation(Timeline timeline, RunReport report) {
        if (timeline.topLevel()) {
            return Optional.empty();
        }
        var tokens = report == null ? "" : " " + report.tokens();
        return Optional.of(Finding.note(timeline.runId(), "subagent-cost",
                "delegated to %s".formatted(timeline.agent()),
                "%s over %d turns%s, dispatched by %s".formatted(
                        humanized(timeline.span()), report == null ? 0 : report.turns(), tokens,
                        timeline.parentRunId())));
    }

    static String humanized(Duration duration) {
        var seconds = duration.toSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        return "%dm %02ds".formatted(seconds / 60, seconds % 60);
    }
}
