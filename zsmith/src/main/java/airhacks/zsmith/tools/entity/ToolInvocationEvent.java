package airhacks.zsmith.tools.entity;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import airhacks.zsmith.Concern;
import airhacks.zsmith.correlation.entity.Correlation;
import static airhacks.zsmith.Concern.Kind.OBSERVABILITY;

@Concern(OBSERVABILITY)
@Name(ToolInvocationEvent.NAME)
@Label("Tool Invocation")
@Category({"zsmith", "tools"})
@Description("Single tool execution requested by the model")
public class ToolInvocationEvent extends Event {

    /// The registered event name, so consumers of the stream name it through the
    /// event that emits it rather than repeating a literal.
    public static final String NAME = "airhacks.zsmith.tools.Invocation";

    @Label("Agent Name")
    public String agentName;

    @Label("Run Id")
    @Description("The chat loop this call was issued in")
    public String runId;

    @Label("Iteration")
    @Description("The turn this call was issued in")
    public int iteration;

    @Label("Tool Use Id")
    @Description("The model's own tool_use id — joins this event to the content block that requested it")
    public String toolUseId;

    @Label("Tool Name")
    public String toolName;

    @Label("Outcome")
    public String outcome;

    @Label("Error Type")
    @Description("Simple name of the exception a failing tool threw, blank otherwise")
    public String errorType;

    @Label("Result Size")
    public int resultSize;

    /// The where-am-I half of the event: which agent, which turn, and which content block
    /// requested the tool. Outcome, error type and result size are filled as the tool runs.
    public static ToolInvocationEvent of(String agentName, Correlation correlation, ToolUse toolUse) {
        var event = new ToolInvocationEvent();
        event.agentName = agentName;
        event.runId = correlation.runId();
        event.iteration = correlation.iteration();
        event.toolUseId = toolUse.id();
        event.toolName = toolUse.name();
        return event;
    }
}
