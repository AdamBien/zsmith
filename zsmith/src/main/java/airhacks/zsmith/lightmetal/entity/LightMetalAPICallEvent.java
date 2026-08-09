package airhacks.zsmith.lightmetal.entity;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name(LightMetalAPICallEvent.NAME)
@Label("LightMetal API Call")
@Category({"zsmith", "lightmetal"})
@Description("Single in-process call to the LightMetal Anthropic-shaped API")
public class LightMetalAPICallEvent extends Event {

    /// The registered event name, so consumers of the stream name it through the
    /// event that emits it rather than repeating a literal.
    public static final String NAME = "airhacks.zsmith.lightmetal.APICall";

    @Label("Run Id")
    @Description("The chat loop this call was made for")
    public String runId;

    @Label("Iteration")
    @Description("The turn this call was made for")
    public int iteration;

    @Label("Model")
    public String model;

    @Label("Stop Reason")
    public String stopReason;

    @Label("Input Tokens")
    public int inputTokens;

    @Label("Output Tokens")
    public int outputTokens;
}
