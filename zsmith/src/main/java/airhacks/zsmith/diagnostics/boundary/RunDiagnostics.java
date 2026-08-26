package airhacks.zsmith.diagnostics.boundary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import jdk.jfr.consumer.EventStream;

import airhacks.zsmith.diagnostics.control.CallTimeline;
import airhacks.zsmith.diagnostics.control.Diagnostics;
import airhacks.zsmith.diagnostics.entity.Finding;
import airhacks.zsmith.telemetry.boundary.EventLog;

/// Reads a finished recording and answers what was avoidable about the runs in it.
///
/// A finished file only: a run still in flight has not yet spent what it is going to, and a
/// verdict on half of it would change every time it was asked for. The same reason `telemetry`
/// prefers replay for any number that gets compared against another run's.
///
/// The recording is read twice — once by [EventLog#replay] for what each run cost, once here for
/// the order its calls came in. Reaching into that fold to piggyback a second consumer would buy
/// one pass over a file at the price of a BC boundary, and the file is bounded at 100 MB.
public interface RunDiagnostics {

    /// `diagnose-recording` — every finding in the recording, top-level runs before the runs they
    /// delegated to.
    static List<Finding> diagnose(Path recording) {
        var reports = EventLog.replay(recording);
        var timeline = new CallTimeline();
        replayInto(recording, timeline);
        return Diagnostics.findings(reports, timeline.timelines());
    }

    /// `report-findings` — the same findings as text, grouped by run.
    static String report(Path recording) {
        var findings = diagnose(recording);
        if (findings.isEmpty()) {
            return "no runs found in " + recording;
        }
        return findings.stream()
                .collect(Collectors.groupingBy(Finding::runId, LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(run -> "run %s\n%s".formatted(run.getKey(), run.getValue().stream()
                        .map(finding -> "  " + finding.line())
                        .collect(Collectors.joining("\n"))))
                .collect(Collectors.joining("\n\n"));
    }

    private static void replayInto(Path recording, CallTimeline timeline) {
        try (var stream = EventStream.openFile(recording)) {
            CallTimeline.eventNames().forEach(name -> stream.onEvent(name, timeline));
            stream.start();
        } catch (IOException e) {
            throw new UncheckedIOException("could not replay recording " + recording, e);
        }
    }
}
