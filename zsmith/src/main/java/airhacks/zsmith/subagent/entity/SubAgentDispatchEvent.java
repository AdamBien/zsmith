package airhacks.zsmith.subagent.entity;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import airhacks.zsmith.Concern;
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
}
