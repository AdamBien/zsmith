package airhacks.zsmith.benchmark.entity;

/**
 * Outcome of a parallel-discrimination run. {@code correct} gates the result (all values
 * retrieved); {@code turns} vs {@code calls} is the headline signal: with {@code tasks}
 * independent calls, a batching agent collapses them into few turns (high {@code maxConcurrency}),
 * a serializing one spreads them across {@code tasks} turns ({@code maxConcurrency == 1}).
 * Renders to the shared {@link BenchmarkRow} columns; {@code maxConcurrency} stays out of
 * the row and is exposed via {@link #diagnostics()} for stderr.
 */
public record EfficiencyResult(int tasks, int calls, int turns, int maxConcurrency, boolean correct) {

    public String markdownRow(String model) {
        return new BenchmarkRow("parallelism", model, this.tasks, this.calls, String.valueOf(this.turns),
                this.correct).markdown();
    }

    public String diagnostics() {
        var gate = this.correct ? "" : " correct=no (not all values retrieved)";
        return "maxConcurrency=%d%s".formatted(this.maxConcurrency, gate);
    }
}
