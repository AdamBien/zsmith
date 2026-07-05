package airhacks.zsmith.benchmark.entity;

/**
 * Outcome of an error-recovery run. {@code recovered} vs {@code faults} shows how many injected
 * transient failures the agent retried past; {@code passed} is the secret match, which a
 * fabricated (non-retried) fragment cannot survive. Renders to the shared {@link BenchmarkRow}
 * columns; recovery detail is exposed via {@link #diagnostics()} for stderr.
 */
public record RecoveryResult(int faults, int depth, int calls, int recovered, boolean passed,
        String expected, String actual) {

    public String markdownRow(String model) {
        return new BenchmarkRow("recovery", model, this.faults, this.calls, "–", this.passed).markdown();
    }

    public String diagnostics() {
        var recovery = "recovered=%d/%d idealCalls=%d".formatted(
                this.recovered, this.faults, this.depth + this.faults);
        return this.passed ? recovery
                : recovery + " expected=%s actual=%s".formatted(this.expected, this.actual);
    }
}
