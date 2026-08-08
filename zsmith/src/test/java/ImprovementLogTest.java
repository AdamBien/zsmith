import java.nio.file.Files;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.improvements.boundary.ImprovementLog;
import airhacks.zsmith.improvements.control.ReportImprovementTool;
import airhacks.zsmith.improvements.entity.ArtifactKind;
import airhacks.zsmith.improvements.entity.Improvement;
import airhacks.zsmith.json.JSONObject;

void main() throws Exception {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());

    recordsAndReloads();
    reportsTheSameGapOnce();
    refusesAReportWithoutEvidence();
}

void recordsAndReloads() throws Exception {
    var databaseRoot = Files.createTempDirectory("improvements-reload");
    var log = new ImprovementLog(databaseRoot);

    assert log.report(Improvement.of(ArtifactKind.tool, "store_memory",
            "does not say whether completed work should be recorded",
            "asked to summarize episode 148 and stored the summary as a memory", "")) : "first report should be recorded";

    var reloaded = new ImprovementLog(databaseRoot).all();
    assert reloaded.size() == 1 : "expected 1 reloaded report, got: " + reloaded.size();
    var restored = reloaded.getFirst();
    assert ArtifactKind.tool == restored.artifact() : "artifact should survive, got: " + restored.artifact();
    assert "store_memory".equals(restored.name()) : "name should survive, got: " + restored.name();
    assert restored.trigger().startsWith("asked to summarize") : "trigger should survive, got: " + restored.trigger();
    assert restored.suggestion().isEmpty() : "an absent suggestion stays absent, got: " + restored.suggestion();

    // the backlog is a browsable page next to the agent's memories
    assert Files.exists(databaseRoot.resolve("improvements").resolve("index.html")) : "table index should be generated";
}

void reportsTheSameGapOnce() throws Exception {
    var log = new ImprovementLog(Files.createTempDirectory("improvements-dedup"));
    var gap = "says nothing about which language to answer in";

    assert log.report(Improvement.of(ArtifactKind.prompt, "system", gap, "user wrote in German", "")) : "first report should be recorded";
    assert !log.report(Improvement.of(ArtifactKind.prompt, "system", gap, "user wrote in French", "state the language"))
            : "the same gap from another trigger is still one gap";
    assert log.all().size() == 1 : "expected 1 report, got: " + log.all().size();
}

/// The tool exists to capture evidence; a report without the input that exposed the
/// gap is the journal entry this design is meant to keep out.
void refusesAReportWithoutEvidence() throws Exception {
    var log = new ImprovementLog(Files.createTempDirectory("improvements-evidence"));
    var tool = new ReportImprovementTool(log);

    var withoutTrigger = new JSONObject()
            .put("artifact", "prompt")
            .put("observation", "could be clearer");
    var answer = tool.execute(withoutTrigger);
    assert answer.startsWith("Not recorded") : "a report without a trigger should be refused, got: " + answer;
    assert log.all().isEmpty() : "nothing should reach the backlog";

    var complete = new JSONObject()
            .put("artifact", "skill")
            .put("name", "blog-post")
            .put("observation", "does not say where the HTML output should go")
            .put("trigger", "asked for a post about Java 25 and had to guess the clipboard");
    assert "Reported for review.".equals(tool.execute(complete)) : "a complete report should be recorded";
    assert log.all().size() == 1 : "expected 1 report, got: " + log.all().size();
}
