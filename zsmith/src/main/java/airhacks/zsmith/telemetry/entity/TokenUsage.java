package airhacks.zsmith.telemetry.entity;

/// What one run spent, summed over its LLM calls. Cache reads are counted apart from input
/// because they are the same context at a different price — a run whose input grows while
/// its cache reads do not is losing the prefix, and that only shows when the two are split.
public record TokenUsage(int input, int output, int cacheRead, int cacheCreation) {

    public static final TokenUsage NONE = new TokenUsage(0, 0, 0, 0);

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(
                this.input + other.input(),
                this.output + other.output(),
                this.cacheRead + other.cacheRead(),
                this.cacheCreation + other.cacheCreation());
    }

    public int total() {
        return this.input + this.output + this.cacheRead + this.cacheCreation;
    }

    @Override
    public String toString() {
        return "in=%d out=%d cache_read=%d cache_create=%d".formatted(
                this.input, this.output, this.cacheRead, this.cacheCreation);
    }
}
