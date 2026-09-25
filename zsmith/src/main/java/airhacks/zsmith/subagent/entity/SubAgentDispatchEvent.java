package airhacks.zsmith.subagent.entity;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import airhacks.zsmith.Concern;
import airhacks.zsmith.correlation.entity.Correlation;
import static airhacks.zsmith.Concern.Kind.OBSERVABILITY;

@Concern(OBSERVABILITY)
@Name(SubAgentDispatchEvent.NAME)
@Label("Sub-Agent Dispatch")
@Category({"zsmith", "subagent"})
@Description("Single delegation of a task to a sub-agent")
public class SubAgentDispatchEvent extends Event {

    /// The registered event name, so consumers of the stream name it through the
    /// event that emits it rather than repeating a literal.
    public static final String NAME = "airhacks.zsmith.subagent.Dispatch";

    @Label("Run Id")
    @Description("The chat loop that delegated — the child's own turns point back to it as their parent run")
    public String runId;

    @Label("Child Agent")
    public String childAgent;

    @Label("Mode")
    public String mode;

    @Label("Depth")
    public int depth;

    @Label("First Run")
    public boolean firstRun;

    @Label("Outcome")
    public String outcome;

    @Label("Task Size")
    public int taskSize;

    /// The where-am-I half of the event: which run delegates to which child, how deep in
    /// the tree, and whether the child runs beside its siblings or one after another.
    /// Outcome and task size are filled as the dispatch plays out.
    public static SubAgentDispatchEvent of(String childAgent, Correlation correlation, boolean parallel, boolean firstRun) {
        var event = new SubAgentDispatchEvent();
        event.childAgent = childAgent;
        event.runId = correlation.runId();
        event.depth = correlation.depth();
        event.mode = parallel ? "parallel" : "sequential";
        event.firstRun = firstRun;
        return event;
    }
}
