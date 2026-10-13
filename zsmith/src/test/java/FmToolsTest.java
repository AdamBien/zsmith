import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.fm.boundary.Fm;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.llm.entity.ToolChoice;

/// Traces fm spec R3.1, R3.2, R3.3, R3.4 — see src/main/java/airhacks/zsmith/fm/package-info.java
/// The `fm` command is src/test/java/fm/FakeFm.java; it logs how it was launched and every turn it receives.

void main() throws IOException {
    ZCfg.loadBaseConfig("zsmith-test-fm-" + ProcessHandle.current().pid());
    var log = Files.createTempFile("zunit-fm", ".log");
    var fm = Fm.of(fakeFm(log), Duration.ofSeconds(10));
    try {
        var called = fm.invokeModel(null, user("call-tool"), tools(), 0.5f, ToolChoice.required);
        var request = lastRequest(log);

        // R3.1 — Where a turn carries tool definitions, the BC shall offer them to the model.
        var offered = request.getJSONArray("tools").getJSONObject(0).getJSONObject("function");
        assert "get_weather".equals(offered.getString("name"))
                && offered.getJSONObject("parameters").getJSONObject("properties").has("city")
                : "R3.1 — expected the tool offered to the model but sent: " + request;

        // R3.2 — If a turn requires a tool call, then the BC shall send it with the tool call left to the model.
        assert !request.has("tool_choice") : "R3.2 — expected the tool call left to the model but sent: " + request;

        // R3.3 — When the model answers with a tool call, the BC shall answer the tool's name, its
        // arguments and the call's id.
        var toolUse = called.getJSONArray("content").getJSONObject(0);
        assert "tool_use".equals(called.getString("stop_reason"))
                && "get_weather".equals(toolUse.getString("name"))
                && "Munich".equals(toolUse.getJSONObject("input").getString("city"))
                && "call_7".equals(toolUse.getString("id"))
                : "R3.3 — expected the tool's name, arguments and call id but got: " + called;

        // R3.4 — When the model answers a turn carrying tool definitions in text, the BC shall answer
        // that text with no tool call.
        var text = fm.invokeModel(null, user("hello"), tools(), 0.5f, ToolChoice.required);
        var blocks = text.getJSONArray("content");
        assert blocks.length() == 1 && "echo: hello".equals(blocks.getJSONObject(0).getString("text"))
                && "end_turn".equals(text.getString("stop_reason"))
                : "R3.4 — expected the text with no tool call but got: " + text;
    } finally {
        fm.stopServer();
        Files.deleteIfExists(log);
    }
}

static JSONArray user(String text) {
    return new JSONArray().put(new JSONObject().put("role", "user").put("content", text));
}

static JSONArray tools() {
    return new JSONArray().put(new JSONObject()
            .put("name", "get_weather")
            .put("description", "answers the weather for a city")
            .put("input_schema", new JSONObject().put("type", "object")
                    .put("properties", new JSONObject().put("city", new JSONObject().put("type", "string")))));
}

static JSONObject lastRequest(Path log) throws IOException {
    return new JSONObject(entries(log, "request").getLast().substring("request ".length()));
}

static String fakeFm(Path log, String... flags) {
    var fake = Path.of("src/test/java/fm/FakeFm.java").toAbsolutePath();
    assert Files.exists(fake) : "fake fm command not found at " + fake;
    return String.join(" ", "java", "--source", "25", "--class-path", classpath(),
            fake.toString(), log.toString(), String.join(" ", flags)).strip();
}

static String classpath() {
    return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(entry -> Path.of(entry).toAbsolutePath().toString())
            .collect(Collectors.joining(File.pathSeparator));
}

static List<String> entries(Path log, String kind) throws IOException {
    if (!Files.exists(log)) {
        return List.of();
    }
    return Files.readAllLines(log).stream().filter(line -> line.startsWith(kind)).toList();
}
