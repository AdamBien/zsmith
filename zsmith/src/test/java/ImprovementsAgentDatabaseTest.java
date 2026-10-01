import static airhacks.zsmith.improvements.Requirement.Rn.*;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.episodicmemory.boundary.EpisodicMemoryStore;
import airhacks.zsmith.improvements.boundary.ImprovementLog;
import airhacks.zsmith.improvements.entity.ArtifactKind;
import airhacks.zsmith.improvements.entity.Improvement;

/// Traces improvements spec R4.1, R4.2 — see src/main/java/airhacks/zsmith/improvements/package-info.java

static final Improvement GAP = new Improvement(ArtifactKind.prompt, "system", "says nothing about tone",
        "asked for a casual reply", "", "2026-01-10T09:00:00Z");
static final String GAP_PAGE = "2026-01-10-090000.html";

void main() throws IOException {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    var suffix = "-" + ProcessHandle.current().pid();
    var duke = "duke" + suffix;
    var jane = "jane" + suffix;
    try {
        ImprovementLog.forAgent(duke).report(GAP);

        var table = ZCfg.agentDatabase(duke).resolve("improvements");
        assert Files.exists(table.resolve(GAP_PAGE)) : "%s — %s — a backlog opened for a named agent: expected %s in %s"
                .formatted(R4_1, R4_1.statement(), GAP_PAGE, table);
        assert ZCfg.agentDatabase(duke).equals(EpisodicMemoryStore.agentPath(duke))
                : "%s — %s — a backlog opened for a named agent: expected the database of its memories %s but got %s"
                        .formatted(R4_1, R4_1.statement(), EpisodicMemoryStore.agentPath(duke), ZCfg.agentDatabase(duke));
        var janes = ImprovementLog.forAgent(jane).all();
        assert janes.isEmpty() : "%s — %s — another named agent: expected an empty backlog but got %s"
                .formatted(R4_1, R4_1.statement(), janes);

        var tableIndex = Files.readString(table.resolve("index.html"));
        assert tableIndex.contains(GAP_PAGE) : "%s — %s — a kept report: expected the table index to link %s but got %s"
                .formatted(R4_2, R4_2.statement(), GAP_PAGE, tableIndex);
        var rootIndex = Files.readString(ZCfg.agentDatabase(duke).resolve("index.html"));
        assert rootIndex.contains("improvements") : "%s — %s — a kept report: expected the database index to link the table but got %s"
                .formatted(R4_2, R4_2.statement(), rootIndex);
    } finally {
        deleteRecursively(ZCfg.agentDirectory(duke));
        deleteRecursively(ZCfg.agentDirectory(jane));
    }
}

static void deleteRecursively(Path directory) throws IOException {
    if (!Files.exists(directory)) {
        return;
    }
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
