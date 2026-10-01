import static airhacks.zsmith.improvements.Requirement.Rn.*;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.improvements.Requirement;
import airhacks.zsmith.improvements.boundary.ImprovementLog;
import airhacks.zsmith.improvements.entity.ArtifactKind;
import airhacks.zsmith.improvements.entity.Improvement;

/// Traces improvements spec R3.1, R3.2, R3.3 — see src/main/java/airhacks/zsmith/improvements/package-info.java

/// A null `damagedPage` leaves the backlog intact.
record Case(Requirement.Rn req, String description, boolean laterSession, String damagedPage) {}

static final Improvement JANUARY = new Improvement(ArtifactKind.prompt, "system", "says nothing about tone",
        "asked for a casual reply", "", "2026-01-10T09:00:00Z");
static final Improvement FEBRUARY = new Improvement(ArtifactKind.skill, "blog-post",
        "does not say where the HTML output should go", "asked for a post about Java 25", "write to the clipboard",
        "2026-02-10T09:00:00Z");
static final Improvement MARCH = new Improvement(ArtifactKind.tool, "store_memory",
        "does not say whether completed work should be recorded", "asked to summarize episode 148", "",
        "2026-03-10T09:00:00Z");
static final List<Improvement> OLDEST_FIRST = List.of(JANUARY, FEBRUARY, MARCH);

void main() throws IOException {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    var cases = List.of(
            new Case(R3_1, "reports made out of order", false, null),
            new Case(R3_2, "reports read in a later session", true, null),
            new Case(R3_3, "a torn page among the reports", true, "<html><body><dl><dt>observation</dt>"),
            new Case(R3_3, "an empty page among the reports", true, ""));

    var base = Files.createTempDirectory("zunit-improvements-backlog");
    try {
        for (var c : cases) {
            var databaseRoot = Files.createTempDirectory(base, "db");
            var log = new ImprovementLog(databaseRoot);
            List.of(MARCH, JANUARY, FEBRUARY).forEach(log::report);
            if (c.damagedPage() != null) {
                Files.writeString(databaseRoot.resolve("improvements").resolve("2026-02-20-090000.html"), c.damagedPage());
            }
            if (c.laterSession()) {
                log = new ImprovementLog(databaseRoot);
            }

            var backlog = log.all();

            assert OLDEST_FIRST.equals(backlog) : "%s — %s — %s: expected %s but got %s"
                    .formatted(c.req(), c.req().statement(), c.description(), OLDEST_FIRST, backlog);
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
