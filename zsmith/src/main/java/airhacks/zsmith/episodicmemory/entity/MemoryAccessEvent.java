package airhacks.zsmith.episodicmemory.entity;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import airhacks.zsmith.Concern;
import airhacks.zsmith.correlation.entity.Correlation;
import static airhacks.zsmith.Concern.Kind.OBSERVABILITY;

@Concern(OBSERVABILITY)
@Name(MemoryAccessEvent.NAME)
@Label("Memory Access")
@Category({"zsmith", "memory"})
@Description("Read or write of a persistent memory store")
public class MemoryAccessEvent extends Event {

    /// The registered event name, so consumers of the stream name it through the
    /// event that emits it rather than repeating a literal.
    public static final String NAME = "airhacks.zsmith.memory.Access";

    @Label("Run Id")
    @Description("The chat loop this access happened in, blank for loads at store construction")
    public String runId;

    @Label("Store")
    public String store;

    @Label("Operation")
    public String operation;

    @Label("Episode Count")
    public int episodeCount;

    @Label("Payload Size")
    public int payloadSize;

    @Label("Outcome")
    public String outcome;

    /// The where-am-I half of the event: which run touched which store, and how. Episode
    /// count, payload size and outcome are filled as the access plays out.
    public static MemoryAccessEvent of(Correlation correlation, String store, String operation) {
        var event = new MemoryAccessEvent();
        event.runId = correlation.runId();
        event.store = store;
        event.operation = operation;
        return event;
    }
}
