package airhacks.zsmith.telemetry.boundary;

import java.util.concurrent.ConcurrentHashMap;

import airhacks.zsmith.telemetry.entity.TokenUsage;

/// What the run in flight has spent so far, accumulated as its LLM calls return.
///
/// The other half of this BC scores runs that are already over: a recording is folded whole and
/// answers the same numbers every time it is read. That is the wrong instrument for a loop still
/// running — the live event stream delivers nothing committed before it started and puts a flush
/// interval between a commit and its delivery, so a per-turn display driven from it would lag or
/// skip turns. This is fed directly by the transports instead, which is why it is exact.
///
/// Keyed by run id and nothing else: a delegated sub-agent is its own run, and folding a child's
/// spend into its parent would leave neither number the cost of anything.
public interface RunTally {

    /// `tally-token-usage` — add one call's usage to what the run has spent.
    ///
    /// Usage reported outside any chat loop carries no run id and is dropped, the same way the
    /// folded event stream drops it: it belongs to no run.
    static void tally(String runId, TokenUsage usage) {
        if (runId == null || runId.isBlank() || usage == null) {
            return;
        }
        Tallies.BY_RUN.merge(runId, usage, TokenUsage::plus);
    }

    /// `report-running-tokens` — what this run alone has spent, zero before its first call returns.
    static TokenUsage runningTokens(String runId) {
        if (runId == null || runId.isBlank()) {
            return TokenUsage.NONE;
        }
        return Tallies.BY_RUN.getOrDefault(runId, TokenUsage.NONE);
    }

    /// `discard-run-tally` — release a finished run's tally.
    ///
    /// A served agent runs many conversations in one process, so a tally kept per run it ever
    /// ran never stops growing. The loop that opened the run closes it.
    static void discard(String runId) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        Tallies.BY_RUN.remove(runId);
    }

    /// Process-wide because the transports that report into it are static and reached from the
    /// loop's own thread — the same reason [airhacks.zsmith.correlation.control.Correlations]
    /// carries the run id rather than passing it down.
    final class Tallies {

        static final ConcurrentHashMap<String, TokenUsage> BY_RUN = new ConcurrentHashMap<>();

        private Tallies() {
        }
    }
}
