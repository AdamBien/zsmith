import static airhacks.zsmith.chunking.Requirement.Rn.*;

import airhacks.zsmith.chunking.Requirement;
import airhacks.zsmith.chunking.control.Chunking;
import airhacks.zsmith.chunking.control.Model;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.llm.entity.LLMResponse;

/// Traces chunking spec R1.1, R1.2, R1.3, R1.4, R1.5, R1.6, R1.7, R1.8 — see src/main/java/airhacks/zsmith/chunking/package-info.java
/// Reads the cuts off the chunks the model is sent; the fake model echoes each chunk.

record Case(Requirement.Rn req, String description, String text, int budget, List<String> expectedChunks) {}

void main() {
    ZCfg.loadBaseConfig("zsmith-test-chunking-" + ProcessHandle.current().pid());
    var unconfigured = Chunking.configured().budget();
    System.setProperty(Chunking.BUDGET_KEY, "50");
    ZCfg.loadBaseConfig("zsmith-test-chunking-" + ProcessHandle.current().pid());
    var configured = Chunking.configured().budget();
    assert unconfigured == 8_000 : "%s — %s: expected 8000 unless configured but got %d"
            .formatted(R1_1, R1_1.statement(), unconfigured);
    assert configured == 50 : "%s — %s: expected the configured 50 but got %d"
            .formatted(R1_1, R1_1.statement(), configured);

    var cases = List.of(
            new Case(R1_2, "a given budget", "x".repeat(25), 10, List.of("x".repeat(10), "x".repeat(10), "xxxxx")),
            new Case(R1_3, "a text shorter than the budget", "Short text. Fits.", 100, List.of("Short text. Fits.")),
            new Case(R1_3, "a text exactly the budget", "Exactly ten", 11, List.of("Exactly ten")),
            new Case(R1_4, "two paragraphs fit, the third does not", "First para.\n\nSecond para.\n\nThird", 30,
                    List.of("First para.\n\nSecond para.", "Third")),
            new Case(R1_4, "a paragraph break before a later sentence end", "One.\n\nTwo. Three. Four", 18,
                    List.of("One.", "Two. Three. Four")),
            new Case(R1_5, "sentence ends only", "One. Two. Three", 12, List.of("One. Two.", "Three")),
            new Case(R1_5, "a single line break is no paragraph break", "One!\nTwo? Three", 12, List.of("One!\nTwo?", "Three")),
            new Case(R1_6, "whitespace only", "alpha beta gamma", 12, List.of("alpha beta", "gamma")),
            new Case(R1_7, "no whitespace at all", "abcdefghijklm", 10, List.of("abcdefghij", "klm")),
            new Case(R1_8, "a run of blank lines", "Para one.\n\n\nPara two.", 12, List.of("Para one.", "Para two.")),
            new Case(R1_8, "a run of spaces", "word   more", 6, List.of("word", "more")));

    for (var c : cases) {
        var sent = new ArrayList<String>();
        var chunking = new Chunking(echo(sent), c.budget(), 0.1f);

        chunking.processText("Repeat the text.", c.text(), c.budget());

        var budget = c.budget();
        assert sent.stream().allMatch(chunk -> chunk.length() <= budget)
                : "%s — %s — %s: expected every chunk within %d characters but sent %s"
                        .formatted(c.req(), c.req().statement(), c.description(), budget, lengths(sent));
        assert c.expectedChunks().equals(sent)
                : "%s — %s — %s: expected the chunks %s but sent %s"
                        .formatted(c.req(), c.req().statement(), c.description(), c.expectedChunks(), sent);
    }

    // R1.1 — with no budget given, the default and the configured budget each cut there.
    var defaultSent = new ArrayList<String>();
    new Chunking(echo(defaultSent), unconfigured, 0.1f).processText("Repeat the text.", "a".repeat(8_001));
    assert lengths(defaultSent).equals(List.of(8_000, 1))
            : "%s — %s: expected chunks of 8000 and 1 characters but sent %s"
                    .formatted(R1_1, R1_1.statement(), lengths(defaultSent));
    var configuredSent = new ArrayList<String>();
    new Chunking(echo(configuredSent), configured, 0.1f).processText("Repeat the text.", "b".repeat(51));
    assert lengths(configuredSent).equals(List.of(50, 1))
            : "%s — %s: expected chunks of 50 and 1 characters but sent %s"
                    .formatted(R1_1, R1_1.statement(), lengths(configuredSent));
}

static Model echo(List<String> sent) {
    return (system, messages, tools, temperature, toolChoice) -> {
        var chunk = messages.getJSONObject(0).getString("content");
        sent.add(chunk);
        return new LLMResponse(new JSONArray().put(new JSONObject().put("type", "text").put("text", chunk)), "end_turn");
    };
}

static List<Integer> lengths(List<String> chunks) {
    return chunks.stream().map(String::length).toList();
}
