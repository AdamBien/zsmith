import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import airhacks.zsmith.telemetry.boundary.RunTally;
import airhacks.zsmith.telemetry.entity.TokenUsage;

/// Traces telemetry spec R3.1–R3.7 — see src/main/java/airhacks/zsmith/telemetry/package-info.java
///
/// The tally is process-wide, so every case here works under its own run id: a shared key would
/// make these cases each other's fixture.

void main() throws Exception {
    accumulatesReportedUsage();
    answersTheRunAlone();
    answersZeroBeforeTheFirstCall();
    dropsUsageWithoutARun();
    accumulatesConcurrentReports();
    releasesADiscardedTally();
    totalsEveryCategory();
}

// R3.1 — When an LLM call reports its token usage for a run, the BC shall add that usage to the
// run's running tally.
void accumulatesReportedUsage() {
    var run = "r31";
    RunTally.tally(run, new TokenUsage(10, 4, 3, 2));
    RunTally.tally(run, new TokenUsage(20, 6, 1, 0));

    var running = RunTally.runningTokens(run);
    if (running.input() != 30 || running.output() != 10 || running.cacheRead() != 4 || running.cacheCreation() != 2)
        throw new AssertionError("R3.1 — expected the two calls summed but got: " + running);
    RunTally.discard(run);
}

// R3.2 — When a run's running tally is requested, the BC shall answer the usage accumulated for
// that run alone.
void answersTheRunAlone() {
    var parent = "r32-parent";
    var child = "r32-child";
    RunTally.tally(parent, new TokenUsage(10, 1, 0, 0));
    RunTally.tally(child, new TokenUsage(500, 50, 0, 0));

    var parentTokens = RunTally.runningTokens(parent);
    if (parentTokens.input() != 10 || parentTokens.output() != 1)
        throw new AssertionError("R3.2 — a delegated run's spend must stay out of its parent's, got: " + parentTokens);
    var childTokens = RunTally.runningTokens(child);
    if (childTokens.input() != 500 || childTokens.output() != 50)
        throw new AssertionError("R3.2 — the child should carry its own spend, got: " + childTokens);
    RunTally.discard(parent);
    RunTally.discard(child);
}

// R3.3 — When no usage has been tallied for a run, the BC shall answer a zero tally.
void answersZeroBeforeTheFirstCall() {
    var running = RunTally.runningTokens("r33-never-tallied");
    if (running.total() != 0)
        throw new AssertionError("R3.3 — a run with no calls yet should tally zero but got: " + running);
}

// R3.4 — If reported usage carries no run identifier, then the BC shall discard it.
void dropsUsageWithoutARun() {
    RunTally.tally("", new TokenUsage(99, 99, 99, 99));
    RunTally.tally(null, new TokenUsage(99, 99, 99, 99));

    if (RunTally.runningTokens("").total() != 0)
        throw new AssertionError("R3.4 — usage outside a run must not accumulate under the blank id");
    if (RunTally.runningTokens(null).total() != 0)
        throw new AssertionError("R3.4 — a missing run id must answer a zero tally, not fail");
}

// R3.5 — When several calls of one run report usage concurrently, the BC shall accumulate every
// one of them.
void accumulatesConcurrentReports() throws Exception {
    var run = "r35";
    var reporters = 64;
    var start = new CountDownLatch(1);
    var done = new CountDownLatch(reporters);
    try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
        for (var i = 0; i < reporters; i++) {
            threads.submit(() -> {
                start.await();
                RunTally.tally(run, new TokenUsage(1, 1, 1, 1));
                done.countDown();
                return null;
            });
        }
        start.countDown();
        if (!done.await(10, TimeUnit.SECONDS))
            throw new AssertionError("R3.5 — concurrent reporters did not finish in time");
    }

    var running = RunTally.runningTokens(run);
    if (running.total() != reporters * 4)
        throw new AssertionError("R3.5 — expected every concurrent report counted (%d) but got: %s"
                .formatted(reporters * 4, running));
    RunTally.discard(run);
}

// R3.6 — When a run's tally is discarded, the BC shall release it.
void releasesADiscardedTally() {
    var run = "r36";
    RunTally.tally(run, new TokenUsage(10, 10, 10, 10));
    RunTally.discard(run);

    var running = RunTally.runningTokens(run);
    if (running.total() != 0)
        throw new AssertionError("R3.6 — a discarded tally should be gone but got: " + running);

    RunTally.tally(run, new TokenUsage(1, 0, 0, 0));
    if (RunTally.runningTokens(run).input() != 1)
        throw new AssertionError("R3.6 — a released run id must start from zero when reused");
    RunTally.discard(run);
}

// R3.7 — The BC shall report a tally's total as the sum of its input, output, cache-read and
// cache-creation counts.
void totalsEveryCategory() {
    var run = "r37";
    RunTally.tally(run, new TokenUsage(12400, 1850, 98000, 4200));

    var total = RunTally.runningTokens(run).total();
    if (total != 116450)
        throw new AssertionError("R3.7 — the total must fold in the cache counts, expected 116450 but got: " + total);
    RunTally.discard(run);
}
