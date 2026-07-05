package airhacks.zsmith.benchmark.entity;

/**
 * Outcome of a single pointer-chasing run. {@code toolCalls} vs {@code depth} reveals whether
 * the agent walked the chain exactly (efficiency); {@code passed} reflects whether the
 * reconstructed secret matched (correctness). Renders to the shared {@link BenchmarkRow}
 * columns; the expected/actual mismatch is kept out of the row and exposed via
 * {@link #diagnostics()} for stderr.
 */
public record BenchmarkResult(int depth, int toolCalls, boolean passed, String expected, String actual) {

    public String markdownRow(String model) {
        return new BenchmarkRow("loop", model, this.depth, this.toolCalls, "–", this.passed).markdown();
    }

    public String diagnostics() {
        if (this.passed) {
            return "";
        }
        return "toolCalls=%d/%d expected=%s actual=%s".formatted(this.toolCalls, this.depth, this.expected, this.actual);
    }
}
