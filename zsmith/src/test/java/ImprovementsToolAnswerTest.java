import static airhacks.zsmith.improvements.Requirement.Rn.*;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.improvements.Requirement;
import airhacks.zsmith.improvements.boundary.ImprovementLog;
import airhacks.zsmith.improvements.control.ReportImprovementTool;
import airhacks.zsmith.json.JSONObject;

/// Traces improvements spec R6.1 — see src/main/java/airhacks/zsmith/improvements/package-info.java

/// `reported` is sent before `input`, to set up a gap that is already on the backlog.
record Case(Requirement.Rn req, String description, List<JSONObject> reported, JSONObject input, String expectedStart,
        String expectedReason) {}

void main() throws IOException {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    var cases = List.of(
            new Case(R6_1, "a complete report", List.of(), complete(), "Reported for review.", ""),
            new Case(R6_1, "the same gap again", List.of(complete()), complete(), "Already reported", ""),
            new Case(R6_1, "a report without a trigger", List.of(), complete().put("trigger", ""), "Not recorded: ",
                    "trigger"),
            new Case(R6_1, "a report without an artifact kind", List.of(), withoutArtifact(), "Not recorded: ",
                    "prompt, skill, tool"),
            new Case(R6_1, "a report about an unknown artifact kind", List.of(), complete().put("artifact", "agent"),
                    "Not recorded: ", "prompt, skill, tool"),
            new Case(R6_1, "a skill report without a name", List.of(), complete().put("name", ""), "Not recorded: ",
                    "name the skill"));

    var base = Files.createTempDirectory("zunit-improvements-answer");
    try {
        for (var c : cases) {
            var tool = new ReportImprovementTool(new ImprovementLog(Files.createTempDirectory(base, "db")));
            c.reported().forEach(tool::execute);

            var answer = tool.execute(c.input());

            assert answer.startsWith(c.expectedStart()) : "%s — %s — %s: expected an answer starting with '%s' but got '%s'"
                    .formatted(c.req(), c.req().statement(), c.description(), c.expectedStart(), answer);
            assert answer.contains(c.expectedReason()) : "%s — %s — %s: expected the reason '%s' but got '%s'"
                    .formatted(c.req(), c.req().statement(), c.description(), c.expectedReason(), answer);
        }
    } finally {
        deleteRecursively(base);
    }
}

static JSONObject complete() {
    return new JSONObject()
            .put("artifact", "skill")
            .put("name", "blog-post")
            .put("observation", "does not say where the HTML output should go")
            .put("trigger", "asked for a post about Java 25 and had to guess the clipboard");
}

static JSONObject withoutArtifact() {
    var input = complete();
    input.remove("artifact");
    return input;
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
