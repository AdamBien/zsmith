import static airhacks.zsmith.chunking.Requirement.Rn.*;

import airhacks.zsmith.chunking.Requirement;
import airhacks.zsmith.chunking.boundary.Chunking;
import airhacks.zsmith.chunking.control.Model;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.llm.entity.LLMResponse;

/// Traces chunking spec R3.1, R3.2 — see src/main/java/airhacks/zsmith/chunking/package-info.java
/// The fake model brackets each chunk and pads its reply with whitespace the join has to trim.

record Case(Requirement.Rn req, String description, String text, int budget, String expected) {}

void main() {
    ZCfg.loadBaseConfig("zsmith-test-chunking-" + ProcessHandle.current().pid());
    Model model = (system, messages, tools, temperature, toolChoice) -> {
        var chunk = messages.getJSONObject(0).getString("content");
        return new LLMResponse(new JSONArray().put(new JSONObject().put("type", "text").put("text", "\n  [" + chunk + "]  \n")),
                "end_turn");
    };

    var cases = List.of(
            new Case(R3_1, "paragraphs", "First para.\n\nSecond para.", 15, "[First para.]\n\n[Second para.]"),
            new Case(R3_1, "a run of blank lines", "First para.\n\n\n\nSecond para.", 15, "[First para.]\n\n\n\n[Second para.]"),
            new Case(R3_1, "sentences and words", "One. Two. Three four", 10, "[One. Two.] [Three four]"),
            new Case(R3_1, "a single chunk", "Fits whole.", 100, "[Fits whole.]"),
            new Case(R3_2, "cuts at the budget", "abcdefghijkl", 5, "[abcde][fghij][kl]"));

    for (var c : cases) {
        var answered = new Chunking(model, c.budget(), 0.1f).processText("Bracket the text.", c.text());

        assert c.expected().equals(answered)
                : "%s — %s — %s: expected [%s] but got [%s]"
                        .formatted(c.req(), c.req().statement(), c.description(), c.expected(), answered);
    }
}
