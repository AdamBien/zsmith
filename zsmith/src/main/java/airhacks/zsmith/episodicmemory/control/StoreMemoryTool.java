package airhacks.zsmith.episodicmemory.control;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.episodicmemory.boundary.EpisodicMemoryStore;
import airhacks.zsmith.episodicmemory.entity.Episode;
import airhacks.zsmith.episodicmemory.entity.MemoryType;
import airhacks.zsmith.tools.control.ToolHandler;

public class StoreMemoryTool implements ToolHandler {

    private final EpisodicMemoryStore store;

    public StoreMemoryTool(EpisodicMemoryStore store) {
        this.store = store;
    }

    @Override
    public String toolName() {
        return "store_memory";
    }

    @Override
    public String description() {
        return """
                Stores a durable fact in long-term memory for future recall. \
                Store only what is still worth knowing in a later, unrelated session. \
                This is not a journal: never record that a task was completed, what was \
                produced in this session, or what the user just asked for. \
                Each memory must be classified with a type: \
                'user' for who the user is — role, expertise, preferences; every agent \
                shares these, so store only what holds regardless of the task; \
                'feedback' for guidance the user gave on how to work — a correction or a \
                confirmed approach, together with the reason behind it; \
                'project' for goals, decisions and constraints of ongoing work that outlive \
                this session; \
                'reference' for pointers to external resources — URLs, dashboards, tickets.""";
    }

    enum Field { content, type }

    @Override
    public JSONObject inputSchema() {
        return ToolHandler.schema(
                Prop.string(Field.content, "The fact to remember, stated so it makes sense on its own in a later session"),
                Prop.stringEnum(Field.type, "The memory type: user, feedback, project, or reference",
                        "user", "feedback", "project", "reference")
        );
    }

    @Override
    public String execute(JSONObject input) {
        var content = input.getString(Field.content.name());
        var type = MemoryType.fromString(input.getString(Field.type.name()));
        var episode = new Episode(content, null, type);
        if (!store.store(episode)) {
            return "Already remembered, nothing stored.";
        }
        return "Memory stored.";
    }
}
