import java.nio.file.Files;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.episodicmemory.boundary.EpisodicMemoryStore;
import airhacks.zsmith.episodicmemory.control.RecallMemoryTool;
import airhacks.zsmith.episodicmemory.control.SearchMemoryTool;
import airhacks.zsmith.episodicmemory.entity.Episode;
import airhacks.zsmith.episodicmemory.entity.MemoryType;
import airhacks.zsmith.json.JSONObject;

/// Traces episodicmemory spec R3.2, R4.9 and R4.10 — see
/// src/main/java/airhacks/zsmith/episodicmemory/package-info.java
///
/// What the store answers with an empty list the model only ever sees as prose, so
/// these three statements are reachable at the tool and nowhere else.

void main() throws Exception {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());

    var store = new EpisodicMemoryStore(Files.createTempDirectory("memory-tools"));
    store.store(Episode.of("zb builds the project without maven", MemoryType.project));

    reportsAnEmptyTypeAsNoneFound(store);
    reportsAnUnmatchedQueryAsNoneFound(store);
    tellsAnEmptyQueryApartFromAnUnmatchedOne(store);
}

// R3.2 — If no memory carries the supplied type, then the BC shall report that none
// was found.
void reportsAnEmptyTypeAsNoneFound(EpisodicMemoryStore store) {
    var recalled = new RecallMemoryTool(store).execute(new JSONObject().put("type", "user"));

    assert "No memories found.".equals(recalled)
            : "R3.2 — an unused type must report none found, got: " + recalled;
}

// R4.9 — If no kept memory carries any term of the query, then the BC shall report
// that none was found.
void reportsAnUnmatchedQueryAsNoneFound(EpisodicMemoryStore store) {
    var found = new SearchMemoryTool(store).execute(new JSONObject().put("query", "kubernetes"));

    assert "No memories found.".equals(found)
            : "R4.9 — an unmatched query must report none found rather than fall back to what is recent, got: "
                    + found;
}

// R4.10 — If the query carries no searchable term, then the BC shall report the query
// as empty rather than return memories.
void tellsAnEmptyQueryApartFromAnUnmatchedOne(EpisodicMemoryStore store) {
    var tool = new SearchMemoryTool(store);

    for (var empty : new String[] { "", "   ", "!!! ???" }) {
        var answered = tool.execute(new JSONObject().put("query", empty));
        assert answered.startsWith("Error:")
                : "R4.10 — '%s' carries no term and must be reported as empty, got: %s".formatted(empty, answered);
        assert !answered.equals("No memories found.")
                : "R4.10 — an empty query must not read like a query that simply matched nothing";
    }
}
