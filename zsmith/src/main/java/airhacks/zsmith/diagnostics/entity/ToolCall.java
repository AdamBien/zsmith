package airhacks.zsmith.diagnostics.entity;

import java.time.Duration;
import java.time.Instant;

/// One tool execution, reduced to what decides whether it was expensive.
///
/// [#resultSize] is the whole point of keeping it: a tool result is appended to the conversation
/// and re-sent on every turn that follows it, so what a large result costs is its size multiplied
/// by the turns left in the run, not the one call that produced it.
///
/// The timings are kept for a different question. A run's calls being spaced far apart says the
/// prefix expired; the tool that was running in the meantime says why, and that is the difference
/// between "the run stood still" and "it waited sixteen minutes for an answer".
public record ToolCall(String runId, Instant started, Instant ended, int iteration,
        String toolName, int resultSize) {

    /// How many turns carried this result after the one that fetched it.
    public int turnsCarried(int turns) {
        return Math.max(0, turns - this.iteration - 1);
    }

    public Duration took() {
        return Duration.between(this.started, this.ended);
    }

    /// Whether this call was what the run was doing between two of its own LLM calls. Containment
    /// rather than overlap: a tool that merely brushes the window was not what held it open.
    public boolean fills(Instant from, Instant to) {
        return !this.started.isBefore(from) && !this.ended.isAfter(to);
    }
}
