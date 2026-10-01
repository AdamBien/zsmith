import static airhacks.zsmith.improvements.Requirement.Rn.*;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.improvements.Requirement;
import airhacks.zsmith.improvements.boundary.ImprovementLog;
import airhacks.zsmith.improvements.entity.ArtifactKind;
import airhacks.zsmith.improvements.entity.Improvement;

/// Traces improvements spec R1.1, R1.2, R1.3, R1.4, R1.5, R1.6, R1.7, R1.8, R1.9 — see src/main/java/airhacks/zsmith/improvements/package-info.java

/// A null `expectedName` marks a report the BC must reject.
record Case(Requirement.Rn req, String description, String artifact, String name, String observation, String trigger,
        String suggestion, String expectedName, String expectedSuggestion) {

    boolean rejected() {
        return this.expectedName == null;
    }
}

static final String GAP = "says nothing about which language to answer in";
static final String TRIGGER = "the user wrote in German";

void main() throws IOException {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    var cases = List.of(
            new Case(R1_1, "a complete prompt report", "prompt", "system", GAP, TRIGGER, "", "system", ""),
            new Case(R1_1, "a complete tool report", "tool", "store_memory", GAP, TRIGGER, "", "store_memory", ""),
            new Case(R1_2, "no observation", "prompt", "system", null, TRIGGER, "", null, null),
            new Case(R1_2, "a blank observation", "prompt", "system", "  ", TRIGGER, "", null, null),
            new Case(R1_2, "no trigger", "prompt", "system", GAP, null, "", null, null),
            new Case(R1_2, "a blank trigger", "prompt", "system", GAP, " ", "", null, null),
            new Case(R1_3, "no artifact kind", null, "system", GAP, TRIGGER, "", null, null),
            new Case(R1_3, "an unknown artifact kind", "agent", "system", GAP, TRIGGER, "", null, null),
            new Case(R1_4, "an upper-case artifact kind", "SKILL", "blog-post", GAP, TRIGGER, "", "blog-post", ""),
            new Case(R1_4, "a mixed-case artifact kind", "Tool", "fetch_url", GAP, TRIGGER, "", "fetch_url", ""),
            new Case(R1_5, "a prompt report without a name", "prompt", null, GAP, TRIGGER, "", "system", ""),
            new Case(R1_5, "a prompt report with a blank name", "prompt", " ", GAP, TRIGGER, "", "system", ""),
            new Case(R1_6, "a skill report without a name", "skill", null, GAP, TRIGGER, "", null, null),
            new Case(R1_6, "a tool report with a blank name", "tool", "", GAP, TRIGGER, "", null, null),
            new Case(R1_7, "a supplied suggestion", "prompt", "system", GAP, TRIGGER, "state the language", "system",
                    "state the language"),
            new Case(R1_7, "no suggestion", "prompt", "system", GAP, TRIGGER, null, "system", ""));

    var base = Files.createTempDirectory("zunit-improvements-report");
    try {
        for (var c : cases) {
            verify(c, Files.createTempDirectory(base, "db"));
        }
        recordsTheTimeOfAReportMadeWithoutOne(Files.createTempDirectory(base, "db"));
        keepsDistinctReportsMadeWithinOneSecond(Files.createTempDirectory(base, "db"));
    } finally {
        deleteRecursively(base);
    }
}

void verify(Case c, Path databaseRoot) {
    var log = new ImprovementLog(databaseRoot);
    var recorded = false;
    IllegalArgumentException rejection = null;
    try {
        var artifact = ArtifactKind.fromString(c.artifact());
        recorded = log.report(Improvement.of(artifact, c.name(), c.observation(), c.trigger(), c.suggestion()));
    } catch (IllegalArgumentException e) {
        rejection = e;
    }
    var kept = new ImprovementLog(databaseRoot).all();
    if (c.rejected()) {
        assert rejection != null : "%s — %s — %s: expected a rejection".formatted(c.req(), c.req().statement(), c.description());
        assert kept.isEmpty() : "%s — %s — %s: expected nothing kept but got %s"
                .formatted(c.req(), c.req().statement(), c.description(), kept);
        return;
    }
    assert rejection == null : "%s — %s — %s: expected the report to be kept but it was rejected: %s"
            .formatted(c.req(), c.req().statement(), c.description(), rejection.getMessage());
    assert recorded : "%s — %s — %s: expected the answer that it was recorded"
            .formatted(c.req(), c.req().statement(), c.description());
    assert kept.size() == 1 : "%s — %s — %s: expected 1 kept report but got %s"
            .formatted(c.req(), c.req().statement(), c.description(), kept);
    var restored = kept.getFirst();
    assert restored.artifact().name().equals(c.artifact().toLowerCase())
            : "%s — %s — %s: expected the artifact kind %s but got %s"
                    .formatted(c.req(), c.req().statement(), c.description(), c.artifact(), restored.artifact());
    assert c.expectedName().equals(restored.name()) : "%s — %s — %s: expected the name %s but got %s"
            .formatted(c.req(), c.req().statement(), c.description(), c.expectedName(), restored.name());
    assert c.expectedSuggestion().equals(restored.suggestion()) : "%s — %s — %s: expected the suggestion '%s' but got '%s'"
            .formatted(c.req(), c.req().statement(), c.description(), c.expectedSuggestion(), restored.suggestion());
}

void recordsTheTimeOfAReportMadeWithoutOne(Path databaseRoot) {
    var before = Instant.now();
    new ImprovementLog(databaseRoot).report(Improvement.of(ArtifactKind.prompt, "system", GAP, TRIGGER, ""));
    var after = Instant.now();

    var recorded = Instant.parse(new ImprovementLog(databaseRoot).all().getFirst().timestamp());
    assert !recorded.isBefore(before) && !recorded.isAfter(after)
            : "%s — %s — a report made without a time: expected a time between %s and %s but got %s"
                    .formatted(R1_8, R1_8.statement(), before, after, recorded);
}

void keepsDistinctReportsMadeWithinOneSecond(Path databaseRoot) {
    var log = new ImprovementLog(databaseRoot);
    var sameSecond = "2026-10-01T12:00:00Z";
    List.of("first gap", "second gap", "third gap").forEach(observation -> log
            .report(new Improvement(ArtifactKind.tool, "fetch_url", observation, TRIGGER, "", sameSecond)));

    var kept = new ImprovementLog(databaseRoot).all().stream().map(Improvement::observation).sorted().toList();
    assert kept.equals(List.of("first gap", "second gap", "third gap"))
            : "%s — %s — three distinct reports within one second: expected all three kept but got %s"
                    .formatted(R1_9, R1_9.statement(), kept);
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
