package airhacks.zsmith.telemetry.entity;

import java.util.HashMap;
import java.util.Map;

/// Everything the event stream knows about one chat loop, folded into a single value.
///
/// Reports combine with [#plus]: every event contributes a report carrying only what it
/// witnessed, and the aggregate is their sum. That keeps the arithmetic in one place instead
/// of spreading counter increments across one branch per event type.
///
/// It says what a run cost and where it went wrong, never why. The why lives in the
/// transcript stored under the same `runId`.
/// `failures` is keyed by what went wrong — the simple name of the exception a tool threw,
/// or the refusal outcome when it never ran at all (`denied`, `not_available`). One map
/// rather than two counters, because "this tool is denied on every run" and "this tool
/// throws on every run" are the same finding at different stages.
public record RunReport(
        String runId,
        String agent,
        int depth,
        int turns,
        int toolCalls,
        int toolFailures,
        Map<String, Integer> failures,
        int subAgentDispatches,
        int apiCalls,
        int retries,
        TokenUsage tokens,
        boolean terminal) {

    public RunReport {
        failures = Map.copyOf(failures);
    }

    public static RunReport empty(String runId) {
        return new RunReport(runId, "", 0, 0, 0, 0, Map.of(), 0, 0, 0, TokenUsage.NONE, false);
    }

    /// Sums two views of the same run. The later `agent` and `depth` win only when the
    /// earlier one never saw a turn event — the events that carry them are the turns.
    public RunReport plus(RunReport other) {
        return new RunReport(
                this.runId,
                this.agent.isBlank() ? other.agent() : this.agent,
                Math.max(this.depth, other.depth()),
                this.turns + other.turns(),
                this.toolCalls + other.toolCalls(),
                this.toolFailures + other.toolFailures(),
                merged(this.failures, other.failures()),
                this.subAgentDispatches + other.subAgentDispatches(),
                this.apiCalls + other.apiCalls(),
                this.retries + other.retries(),
                this.tokens.plus(other.tokens()),
                this.terminal || other.terminal());
    }

    static Map<String, Integer> merged(Map<String, Integer> left, Map<String, Integer> right) {
        var sum = new HashMap<>(left);
        right.forEach((type, count) -> sum.merge(type, count, Integer::sum));
        return sum;
    }

    /// A run that never reached a terminal turn hit the iteration limit or died mid-loop —
    /// the single most useful thing to filter a recording by.
    public boolean incomplete() {
        return !this.terminal;
    }

    public String summary() {
        return "%s agent=%s depth=%d turns=%d tools=%d/%d failures=%s subagents=%d api=%d retries=%d %s%s"
                .formatted(this.runId, this.agent, this.depth, this.turns, this.toolFailures, this.toolCalls,
                        this.failures.isEmpty() ? "none" : this.failures, this.subAgentDispatches,
                        this.apiCalls, this.retries, this.tokens, this.terminal ? "" : " INCOMPLETE");
    }
}
