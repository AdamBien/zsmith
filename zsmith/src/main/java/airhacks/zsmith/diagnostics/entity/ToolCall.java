package airhacks.zsmith.diagnostics.entity;

/// One tool execution, reduced to what decides whether it was expensive.
///
/// [#resultSize] is the whole point: a tool result is appended to the conversation and re-sent on
/// every turn that follows it, so what a large result costs is its size multiplied by the turns
/// left in the run, not the one call that produced it.
public record ToolCall(String runId, int iteration, String toolName, int resultSize) {

    /// How many turns carried this result after the one that fetched it.
    public int turnsCarried(int turns) {
        return Math.max(0, turns - this.iteration - 1);
    }
}
