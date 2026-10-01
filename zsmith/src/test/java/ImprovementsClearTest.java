import static airhacks.zsmith.improvements.Requirement.Rn.*;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.improvements.Requirement;
import airhacks.zsmith.improvements.boundary.ImprovementLog;
import airhacks.zsmith.improvements.entity.ArtifactKind;
import airhacks.zsmith.improvements.entity.Improvement;

/// Traces improvements spec R5.1 — see src/main/java/airhacks/zsmith/improvements/package-info.java

record Case(Requirement.Rn req, String description, List<String> observations) {}

void main() throws IOException {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    var cases = List.of(
            new Case(R5_1, "a backlog with reports", List.of("says nothing about tone", "says nothing about length")),
            new Case(R5_1, "an empty backlog", List.of()));

    var base = Files.createTempDirectory("zunit-improvements-clear");
    try {
        for (var c : cases) {
            var databaseRoot = Files.createTempDirectory(base, "db");
            var log = new ImprovementLog(databaseRoot);
            c.observations().forEach(observation -> log
                    .report(Improvement.of(ArtifactKind.prompt, "system", observation, "asked for a reply", "")));

            log.clear();

            assert log.all().isEmpty() : "%s — %s — %s: expected nothing in the run but got %s"
                    .formatted(c.req(), c.req().statement(), c.description(), log.all());
            var reread = new ImprovementLog(databaseRoot).all();
            assert reread.isEmpty() : "%s — %s — %s: expected nothing in the database but got %s"
                    .formatted(c.req(), c.req().statement(), c.description(), reread);
            assert !Files.exists(databaseRoot.resolve("improvements")) : "%s — %s — %s: expected the table removed"
                    .formatted(c.req(), c.req().statement(), c.description());
        }
    } finally {
        deleteRecursively(base);
    }
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
