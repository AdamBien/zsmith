package airhacks.zsmith.claude.entity;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import airhacks.zsmith.Concern;
import static airhacks.zsmith.Concern.Kind.OBSERVABILITY;

@Concern(OBSERVABILITY)
@Name(ClaudeAPICallEvent.NAME)
@Label("Claude API Call")
@Category({"zsmith", "claude"})
@Description("Single HTTP call to the Anthropic Messages API")
public class ClaudeAPICallEvent extends Event {

    /// The registered event name, so consumers of the stream name it through the
    /// event that emits it rather than repeating a literal.
    public static final String NAME = "airhacks.zsmith.claude.APICall";

    @Label("Run Id")
    @Description("The chat loop this call was made for")
    public String runId;

    @Label("Iteration")
    @Description("The turn this call was made for")
    public int iteration;

    @Label("Model")
    public String model;

    @Label("Attempt")
    @Description("1 for the first call, 2 for the fallback model after a 529 — a retry sequence stays countable")
    public int attempt;

    @Label("HTTP Status")
    public int statusCode;

    @Label("Stop Reason")
    public String stopReason;

    @Label("Input Tokens")
    public int inputTokens;

    @Label("Output Tokens")
    public int outputTokens;

    @Label("Cache Read Tokens")
    public int cacheReadTokens;

    @Label("Cache Creation Tokens")
    public int cacheCreationTokens;
}
