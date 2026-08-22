import java.util.Map;
import java.util.function.Predicate;

import airhacks.zsmith.agent.entity.AgentTurnEvent;
import airhacks.zsmith.telemetry.boundary.EventLog;
import airhacks.zsmith.telemetry.entity.RunReport;

/// Traces telemetry spec R1.2, R1.5 and R1.6 — see
/// src/main/java/airhacks/zsmith/telemetry/package-info.java
///
/// Live observation is the half of this BC that cannot be scored deterministically: a flush
/// interval sits between a commit and its delivery, so every wait here polls to a deadline
/// rather than assuming a duration.

static final long TIMEOUT_MILLIS = 20_000;
static final String RUN = "live-run";

void main() throws Exception {
    var log = EventLog.live();
    try {
        var snapshot = foldsFlushedEvents(log);
        answersASnapshotNotAView(log, snapshot);
    } finally {
        log.close();
    }
    releasesTheSubscriptionOnceStopped(log);
}

// R1.2 — When live observation is started, the BC shall fold this JVM's events into reports as
// they are flushed.
Map<String, RunReport> foldsFlushedEvents(EventLog log) throws Exception {
    turn(RUN, 0, false);

    var arrived = awaitReports(log, reports -> reports.containsKey(RUN));
    if (arrived == null)
        throw new AssertionError("R1.2 — a committed turn never reached the live fold within the timeout");
    if (arrived.get(RUN).turns() != 1)
        throw new AssertionError("R1.2 — expected the flushed turn folded, got: " + arrived.get(RUN).summary());
    return arrived;
}

// R1.5 — When reports are requested during live observation, the BC shall answer a snapshot of
// what has arrived rather than a view onto what is still arriving.
void answersASnapshotNotAView(EventLog log, Map<String, RunReport> snapshot) throws Exception {
    var turnsWhenTaken = snapshot.get(RUN).turns();

    turn(RUN, 1, false);
    turn(RUN, 2, true);

    var later = awaitReports(log, reports -> reports.get(RUN) != null && reports.get(RUN).turns() > turnsWhenTaken);
    if (later == null)
        throw new AssertionError("R1.5 — the later turns never arrived, so the snapshot cannot be judged");

    if (snapshot.get(RUN).turns() != turnsWhenTaken)
        throw new AssertionError("R1.5 — the earlier answer changed underneath its holder, now: "
                + snapshot.get(RUN).summary());
    if (later.get(RUN).turns() != 3)
        throw new AssertionError("R1.5 — a later request should see every turn, got: " + later.get(RUN).summary());
}

// R1.6 — When a live observation is stopped, the BC shall release its subscription to the event
// stream.
void releasesTheSubscriptionOnceStopped(EventLog closed) throws Exception {
    var stoppedRun = "after-close";
    var before = closed.reports();

    turn(stoppedRun, 0, true);
    // two flush intervals: long enough that a still-subscribed stream would have delivered it
    Thread.sleep(3_000);

    var after = closed.reports();
    if (after.containsKey(stoppedRun))
        throw new AssertionError("R1.6 — a stopped observation kept folding events, got: " + after.keySet());
    if (after.size() != before.size())
        throw new AssertionError("R1.6 — a stopped observation grew from %d to %d runs"
                .formatted(before.size(), after.size()));
}

Map<String, RunReport> awaitReports(EventLog log, Predicate<Map<String, RunReport>> satisfied) throws Exception {
    var deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
    while (System.currentTimeMillis() < deadline) {
        var reports = log.reports();
        if (satisfied.test(reports)) {
            return reports;
        }
        Thread.sleep(100);
    }
    return null;
}

void turn(String runId, int iteration, boolean terminal) {
    var event = new AgentTurnEvent();
    event.begin();
    event.agentName = "live-agent";
    event.runId = runId;
    event.parentRunId = "";
    event.depth = 0;
    event.iteration = iteration;
    event.stopReason = terminal ? "end_turn" : "tool_use";
    event.toolUseCount = 0;
    event.parallelToolCount = 0;
    event.sequentialToolCount = 0;
    event.terminal = terminal;
    event.commit();
}
