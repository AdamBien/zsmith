package airhacks.zsmith.diagnostics.entity;

import java.time.Duration;
import java.time.Instant;

/// One LLM call, kept in the order it was made.
///
/// The fold in `telemetry` sums cache counts across a run, which is the right shape for what a run
/// cost and the wrong one for why: a run that read 1.2M cached tokens and re-created 200K looks
/// identical whether the re-creation was four sub-agents starting cold or one prefix expiring
/// mid-conversation. Only the sequence tells those apart, so the sequence is kept.
public record Call(String runId, Instant started, Instant ended, int iteration,
        int cacheRead, int cacheCreation) {

    /// The idle stretch between the previous call of this run finishing and this one starting.
    /// Measured within a run, never across runs: a parent waiting on a delegated sub-agent is not
    /// idle, and its own calls are the only ones whose spacing decides whether its prefix survived.
    public Duration since(Call previous) {
        return Duration.between(previous.ended(), this.started);
    }

    /// A first call has nothing cached by definition, which is why a run's first call can never be
    /// evidence of an expiry.
    public boolean readNothingFromCache() {
        return this.cacheRead == 0;
    }
}
