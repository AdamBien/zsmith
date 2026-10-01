import static airhacks.zsmith.improvements.Requirement.Rn.*;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.improvements.Requirement;
import airhacks.zsmith.improvements.boundary.ImprovementLog;
import airhacks.zsmith.improvements.entity.ArtifactKind;
import airhacks.zsmith.improvements.entity.Improvement;

/// Traces improvements spec R2.1, R2.2, R2.3 — see src/main/java/airhacks/zsmith/improvements/package-info.java

record Case(Requirement.Rn req, String description, Improvement second, boolean laterSession, boolean expectedRecorded) {}

static final String GAP = "says nothing about which language to answer in";
static final Improvement FIRST = new Improvement(ArtifactKind.prompt, "system", GAP, "the user wrote in German", "",
        "2026-10-01T12:00:00Z");

void main() throws IOException {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    var cases = List.of(
            new Case(R2_1, "the same gap again", reported(ArtifactKind.prompt, "system", GAP, "the user wrote in German", ""),
                    false, false),
            new Case(R2_2, "the same gap from another trigger",
                    reported(ArtifactKind.prompt, "system", GAP, "the user wrote in French", ""), false, false),
            new Case(R2_2, "the same gap with a suggestion",
                    reported(ArtifactKind.prompt, "system", GAP, "the user wrote in German", "state the language"), false, false),
            new Case(R2_2, "the same gap at another time", new Improvement(ArtifactKind.prompt, "system", GAP,
                    "the user wrote in German", "", "2026-10-02T08:00:00Z"), false, false),
            new Case(R2_2, "an observation differing in case",
                    reported(ArtifactKind.prompt, "system", GAP.toUpperCase(), "the user wrote in German", ""), false, true),
            new Case(R2_2, "an observation differing in trailing whitespace",
                    reported(ArtifactKind.prompt, "system", GAP + " ", "the user wrote in German", ""), false, true),
            new Case(R2_2, "the same observation about another name",
                    reported(ArtifactKind.prompt, "orchestrator", GAP, "the user wrote in German", ""), false, true),
            new Case(R2_2, "the same observation about another artifact kind",
                    reported(ArtifactKind.skill, "system", GAP, "the user wrote in German", ""), false, true),
            new Case(R2_3, "the same gap in a later session",
                    reported(ArtifactKind.prompt, "system", GAP, "the user wrote in Polish", ""), true, false),
            new Case(R2_3, "a new gap in a later session",
                    reported(ArtifactKind.prompt, "system", "says nothing about tone", "the user wrote in Polish", ""), true, true));

    var base = Files.createTempDirectory("zunit-improvements-sameness");
    try {
        for (var c : cases) {
            var databaseRoot = Files.createTempDirectory(base, "db");
            var log = new ImprovementLog(databaseRoot);
            log.report(FIRST);
            if (c.laterSession()) {
                log = new ImprovementLog(databaseRoot);
            }

            var recorded = log.report(c.second());

            assert recorded == c.expectedRecorded() : "%s — %s — %s: expected recorded=%s but got %s"
                    .formatted(c.req(), c.req().statement(), c.description(), c.expectedRecorded(), recorded);
            var kept = new ImprovementLog(databaseRoot).all();
            var expectedCount = c.expectedRecorded() ? 2 : 1;
            assert kept.size() == expectedCount : "%s — %s — %s: expected %d kept reports but got %s"
                    .formatted(c.req(), c.req().statement(), c.description(), expectedCount, kept);
            assert kept.contains(FIRST) : "%s — %s — %s: expected the first report kept unchanged but got %s"
                    .formatted(c.req(), c.req().statement(), c.description(), kept);
        }
    } finally {
        deleteRecursively(base);
    }
}

static Improvement reported(ArtifactKind artifact, String name, String observation, String trigger, String suggestion) {
    return Improvement.of(artifact, name, observation, trigger, suggestion);
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
