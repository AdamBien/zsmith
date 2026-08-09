package airhacks.zsmith.openai.entity;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name(OpenAIAPICallEvent.NAME)
@Label("OpenAI API Call")
@Category({"zsmith", "openai"})
@Description("Single HTTP call to an OpenAI-compatible Chat Completions endpoint")
public class OpenAIAPICallEvent extends Event {

    /// The registered event name, so consumers of the stream name it through the
    /// event that emits it rather than repeating a literal.
    public static final String NAME = "airhacks.zsmith.openai.APICall";

    @Label("Run Id")
    @Description("The chat loop this call was made for")
    public String runId;

    @Label("Iteration")
    @Description("The turn this call was made for")
    public int iteration;

    @Label("Model")
    public String model;

    @Label("HTTP Status")
    public int statusCode;

    @Label("Stop Reason")
    public String stopReason;

    @Label("Input Tokens")
    public int inputTokens;

    @Label("Output Tokens")
    public int outputTokens;
}
