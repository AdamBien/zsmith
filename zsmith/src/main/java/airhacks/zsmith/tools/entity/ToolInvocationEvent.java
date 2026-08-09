package airhacks.zsmith.tools.entity;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

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
}
