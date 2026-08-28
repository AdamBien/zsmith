package airhacks.zsmith.diagnostics.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/// One run's events in order, which is the half of a recording the per-run fold discards.
///
/// Keyed by run and never merged upward: a delegated sub-agent is its own run, so folding a
/// child's calls into its parent would leave neither sequence the shape of anything that happened.
/// The same reasoning the running tally is built on.
public record Timeline(String runId, String agent, String parentRunId, int depth,
        List<Call> calls, List<ToolCall> toolCalls, int toolUses, int turnsWithTools) {

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

    /// What each tool left in the conversation, dearest first. Grouped by tool because a tool
    /// called three times is one thing to fix, and ranked by what was carried rather than by result
    /// size because those two orders disagree — the largest single result is routinely not the
    /// largest cost.
    public List<CarriedContext> carried(int turns) {
        return this.toolCalls.stream()
                .collect(Collectors.groupingBy(ToolCall::toolName))
                .values().stream()
                .map(calls -> CarriedContext.of(calls, turns))
                .sorted(Comparator.comparingLong(CarriedContext::carriedBytes).reversed())
                .toList();
    }

    /// What the run was doing between two of its own LLM calls — the longest tool call that fits
    /// entirely inside the window. The longest rather than the first because a turn can issue
    /// several, and the one that held the window open is the one worth naming.
    public Optional<ToolCall> blockedOn(Instant from, Instant to) {
        return this.toolCalls.stream()
                .filter(call -> call.fills(from, to))
                .max(Comparator.comparing(ToolCall::took));
    }

    /// A run nobody delegated to. Reported first, because the sub-agent runs only make sense
    /// underneath the one that dispatched them.
    public boolean topLevel() {
        return this.depth == 0;
    }

    /// Tool calls per turn that asked for any — how many the model got out of one round trip.
    ///
    /// Counted per turn rather than by how they were executed: whether two tools ran concurrently
    /// is decided by the tools themselves, and a run using only sequential ones is not a run that
    /// serialized anything. What a turn costs is the round trip through the model, so a turn that
    /// asked for eight tools cost the same as one that asked for one.
    public double toolsPerTurn() {
        return this.turnsWithTools == 0 ? 0 : (double) this.toolUses / this.turnsWithTools;
    }
}
