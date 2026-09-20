package airhacks.zsmith.episodicmemory.control;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.episodicmemory.boundary.EpisodicMemoryStore;
import airhacks.zsmith.episodicmemory.entity.MemoryType;
import airhacks.zsmith.tools.boundary.Tool;

public class SearchMemoryTool implements Tool {

    static final int DEFAULT_RESULTS = 10;

    /// Told apart from "nothing matched", because the two ask the model for
    /// different things: one to phrase a query, the other to accept that the
    /// memory does not exist.
    static final String EMPTY_QUERY = "Error: the query carries no searchable term.";

    private final EpisodicMemoryStore store;

    public SearchMemoryTool(EpisodicMemoryStore store) {
        this.store = store;
    }

    @Override
    public String toolName() {
        return "search_memory";
    }

    @Override
    public String description() {
        return """
                Finds the memories a question is about, most relevant first. \
                Prefer it over recall_memory whenever you know what you are looking \
                for: recall answers what is most recent, this answers what matches. \
                A term matches any word beginning with it and the ranking is lexical, \
                so a query has to share its words with the memory it should find — \
                when nothing comes back, ask again in the words the memory would have \
                been written in. Optionally filter by type (user, feedback, project, \
                reference) or cap the number of memories returned.""";
    }

    enum Field { query, type, limit }

    @Override
    public JSONObject inputSchema() {
        return Tool.schema(
                Prop.string(Field.query, "What to look for, in the words a memory about it would carry"),
                Prop.stringEnum(Field.type, "Optional type to search within",
                        "user", "feedback", "project", "reference").optional(),
                Prop.integer(Field.limit, "Maximum number of memories to return. Defaults to 10.").optional()
        );
    }

    @Override
    public String execute(JSONObject input) {
        var query = input.optString(Field.query.name(), "");
        if (Relevance.terms(query).isEmpty()) {
            return EMPTY_QUERY;
        }
        var type = MemoryType.fromString(input.optString(Field.type.name(), null));
        var limit = input.optInt(Field.limit.name(), DEFAULT_RESULTS);
        return Memories.format(this.store.search(query, type, limit));
    }
}
