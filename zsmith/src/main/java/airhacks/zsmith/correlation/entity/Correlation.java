package airhacks.zsmith.correlation.entity;

/// Identifies the chat loop an event belongs to. `runId` groups every event of one
/// `chat` / `act` invocation — turns, tool calls, LLM calls, sub-agent dispatches —
/// `iteration` locates the turn within it, and `depth` says how far down the sub-agent
/// tree that loop is running.
///
/// This is what makes the JFR events joinable: tool calls issued in one turn run on
/// their own virtual threads, so `eventThread` identifies nothing and a time window
/// is the only alternative — which stops working the moment two agents run in one JVM.
///
/// `depth` lives here rather than in `subagent` because it is the same ambient
/// where-am-I context as the rest, and it has to survive the same thread hop.
public record Correlation(String runId, int iteration, int depth) {

    /// Stands in for work happening outside any chat loop — a skill read during store
    /// initialization, a tool invoked directly from a test. The blank run id keeps the
    /// per-chunk string pool from growing an entry per unrelated event.
    public static final Correlation NONE = new Correlation("", -1, 0);

    public Correlation {
        if (runId == null) {
            runId = "";
        }
    }

    /// The context a delegated sub-agent runs in: one level further down, still pointing
    /// at the run that delegated.
    public Correlation deeper() {
        return new Correlation(this.runId, this.iteration, this.depth + 1);
    }

    public boolean isKnown() {
        return !this.runId.isBlank();
    }
}
