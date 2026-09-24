package airhacks.zsmith.agent.entity;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import airhacks.zsmith.Concern;
import airhacks.zsmith.correlation.entity.Correlation;
import static airhacks.zsmith.Concern.Kind.OBSERVABILITY;

@Concern(OBSERVABILITY)
@Name(AgentTurnEvent.NAME)
@Label("Agent Turn")
@Category({"zsmith", "agent"})
@Description("One iteration of the chat loop: Claude invocation plus optional tool execution")
public class AgentTurnEvent extends Event {

    /// The registered event name, so consumers of the stream name it through the
    /// event that emits it rather than repeating a literal.
    public static final String NAME = "airhacks.zsmith.agent.Turn";

    @Label("Agent Name")
    public String agentName;

    @Label("Run Id")
    @Description("Groups every event of one chat loop")
    public String runId;

    @Label("Parent Run Id")
    @Description("The run that delegated to this one, blank for a top-level chat")
    public String parentRunId;

    @Label("Depth")
    @Description("Position in the sub-agent tree, 0 for a top-level chat")
    public int depth;

    @Label("Iteration")
    public int iteration;

    @Label("Stop Reason")
    public String stopReason;

    @Label("Tool Use Count")
    public int toolUseCount;

    @Label("Parallel Tool Count")
    public int parallelToolCount;

    @Label("Sequential Tool Count")
    public int sequentialToolCount;

    @Label("Terminal")
    public boolean terminal;

    /// The where-am-I half of the event, taken from the correlation the turn runs under and
    /// the run that delegated to it. The outcome fields are filled as the turn plays out.
    public static AgentTurnEvent of(String agentName, Correlation correlation, Correlation parent) {
        var event = new AgentTurnEvent();
        event.agentName = agentName;
        event.runId = correlation.runId();
        event.parentRunId = parent.runId();
        event.depth = correlation.depth();
        event.iteration = correlation.iteration();
        return event;
    }
}
