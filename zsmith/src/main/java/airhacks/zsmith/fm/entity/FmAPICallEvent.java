package airhacks.zsmith.fm.entity;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import airhacks.zsmith.Concern;
import airhacks.zsmith.correlation.entity.Correlation;
import static airhacks.zsmith.Concern.Kind.OBSERVABILITY;

@Concern(OBSERVABILITY)
@Name(FmAPICallEvent.NAME)
@Label("Fm API Call")
@Category({"zsmith", "fm"})
@Description("Single HTTP call to the local server of the on-device Apple Foundation Model")
public class FmAPICallEvent extends Event {

    /// The registered event name, so consumers of the stream name it through the
    /// event that emits it rather than repeating a literal.
    public static final String NAME = "airhacks.zsmith.fm.APICall";

    @Label("Run Id")
    @Description("The chat loop this call was made for")
    public String runId;

    @Label("Iteration")
    @Description("The turn this call was made for")
    public int iteration;

    @Label("Depth")
    @Description("How far down the sub-agent tree the calling loop runs")
    public int depth;

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

    /// The where-am-I half of the event: which run, turn and depth is calling and which
    /// model. Status, stop reason and token counts arrive with the response.
    public static FmAPICallEvent of(Correlation correlation, String model) {
        var event = new FmAPICallEvent();
        event.runId = correlation.runId();
        event.iteration = correlation.iteration();
        event.depth = correlation.depth();
        event.model = model;
        return event;
    }
}
