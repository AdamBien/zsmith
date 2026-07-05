package airhacks.zsmith.benchmark.entity;

/**
 * Normalized result row shared by all benchmarks. Every run renders to one markdown table
 * row with identical columns, so results from different benchmarks and sizes line up in a
 * single summary table (see {@code benchmarks/README.md}). {@code turns} is a string because
 * not every benchmark tracks turns — pointer chasing reports {@code "–"}.
 */
public record BenchmarkRow(String benchmark, String model, int size, int calls, String turns, boolean passed) {

    public static final String HEADER = """
            | Benchmark | Model | Size | Calls | Turns | Result |
            |-----------|-------|------|-------|-------|--------|""";

    public String markdown() {
        var result = this.passed ? "PASS" : "FAIL";
        return "| %s | %s | %d | %d | %s | %s |"
                .formatted(this.benchmark, this.model, this.size, this.calls, this.turns, result);
    }
}
