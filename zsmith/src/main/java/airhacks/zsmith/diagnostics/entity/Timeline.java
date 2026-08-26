package airhacks.zsmith.diagnostics.entity;

import java.time.Duration;
import java.util.List;

/// One run's events in order, which is the half of a recording the per-run fold discards.
///
/// Keyed by run and never merged upward: a delegated sub-agent is its own run, so folding a
/// child's calls into its parent would leave neither sequence the shape of anything that happened.
/// The same reasoning the running tally is built on.
public record Timeline(String runId, String agent, String parentRunId, int depth,
        List<Call> calls, List<ToolCall> toolCalls, int toolUses, int parallelToolUses) {

    public Timeline {
        calls = List.copyOf(calls);
        toolCalls = List.copyOf(toolCalls);
    }

    /// Wall clock from the run's first call starting to its last one finishing. Zero for a run
    /// whose calls never made it into the recording.
    public Duration span() {
        if (this.calls.isEmpty()) {
            return Duration.ZERO;
        }
        return Duration.between(this.calls.getFirst().started(), this.calls.getLast().ended());
    }

    /// A run nobody delegated to. Reported first, because the sub-agent runs only make sense
    /// underneath the one that dispatched them.
    public boolean topLevel() {
        return this.depth == 0;
    }

    /// The share of tool calls the model asked for in one turn rather than one at a time.
    /// Undefined without tool use, which the callers check before asking.
    public double batchedShare() {
        return this.toolUses == 0 ? 1 : (double) this.parallelToolUses / this.toolUses;
    }
}
