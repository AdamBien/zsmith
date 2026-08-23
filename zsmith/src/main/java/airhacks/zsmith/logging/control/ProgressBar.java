package airhacks.zsmith.logging.control;

import airhacks.zsmith.telemetry.entity.TokenUsage;

/// The live view of a run: how far the loop has gone, how much it has invoked, and what it has
/// spent so far.
///
/// The spend is passed in rather than accumulated here — the run's tally is kept by the BC that
/// owns run cost, and a second counter in the display would be a second answer to the same
/// question. This only renders.
public class ProgressBar {

    static final int BAR_WIDTH = 20;
    static final String FILLED = "█";
    static final String EMPTY = "░";
    static final String UP = "↑";
    static final String DOWN = "↓";
    static final String RESET = "\u001B[0m";

    private final int maxIterations;
    private int llmInvocations;
    private int toolInvocations;

    public ProgressBar(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public void addLLMInvocation() {
        this.llmInvocations++;
    }

    public void addToolInvocations(int count) {
        this.toolInvocations += count;
    }

    public void update(int iteration, TokenUsage tokens) {
        System.out.println(render(iteration, tokens));
    }

    String render(int iteration, TokenUsage tokens) {
        int filled = (int) ((double) iteration / maxIterations * BAR_WIDTH);
        int empty = BAR_WIDTH - filled;

        var blue = Log.Color.BLUE.code;
        var violet = Log.Color.VIOLET.code;
        var cyan = Log.Color.CYAN.code;
        var magenta = Log.Color.MAGENTA.code;
        var yellow = Log.Color.YELLOW.code;

        return blue + "[" + FILLED.repeat(filled) + RESET
                + violet + EMPTY.repeat(empty) + RESET
                + blue + "]" + RESET
                + "  " + iteration + "/" + maxIterations
                + "  " + cyan + "llm: " + llmInvocations + RESET
                + "  " + magenta + "tools: " + toolInvocations + RESET
                + "  " + yellow + "tok: " + tokens.input() + UP + " " + tokens.output() + DOWN + RESET;
    }

    /// The total folds in the cache counts the per-turn line leaves out, so it reads larger than
    /// the two numbers beside it — that gap is the cached prefix, and it is the point.
    public void summary(TokenUsage tokens) {
        var cyan = Log.Color.CYAN.code;
        var magenta = Log.Color.MAGENTA.code;
        var green = Log.Color.GREEN.code;
        var yellow = Log.Color.YELLOW.code;
        System.out.println(green + "--- summary ---" + RESET
                + "  " + cyan + "llm: " + llmInvocations + RESET
                + "  " + magenta + "tools: " + toolInvocations + RESET
                + "  " + yellow + "tokens: in=%d out=%d total=%d"
                        .formatted(tokens.input(), tokens.output(), tokens.total()) + RESET);
    }
}
