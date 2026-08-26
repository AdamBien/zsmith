package airhacks.zsmith.diagnostics.entity;

/// One thing worth knowing about a run that has already finished.
///
/// A finding is a judgement, which is what separates it from a [airhacks.zsmith.telemetry.entity.RunReport]:
/// the report says what a run cost, this says whether that was avoidable. The judgement is
/// arithmetic over recorded fields and nothing else — no model reads the recording to produce one.
///
/// [#evidence] carries the measured values the verdict rests on, so a finding can be argued with
/// rather than believed. A [Severity#PASS] finding is reported for the same reason: "no retries"
/// is a result, and leaving it out would make a clean run indistinguishable from an unchecked one.
public record Finding(String runId, Severity severity, String kind, String summary, String evidence) {

    /// How much a finding is worth acting on. Deliberately three: a run that is fine has to be
    /// able to say so, or every report reads as a list of problems.
    public enum Severity {
        /// Cost or correctness that was avoidable.
        WARNING,
        /// Worth knowing, not necessarily worth changing.
        NOTE,
        /// Checked and healthy.
        PASS
    }

    public static Finding warning(String runId, String kind, String summary, String evidence) {
        return new Finding(runId, Severity.WARNING, kind, summary, evidence);
    }

    public static Finding note(String runId, String kind, String summary, String evidence) {
        return new Finding(runId, Severity.NOTE, kind, summary, evidence);
    }

    public static Finding pass(String runId, String kind, String summary, String evidence) {
        return new Finding(runId, Severity.PASS, kind, summary, evidence);
    }

    public String line() {
        return "%-7s %-22s %s — %s".formatted(this.severity, this.kind, this.summary, this.evidence);
    }
}
