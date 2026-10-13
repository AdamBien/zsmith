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

/// Traces fm spec R2.1, R2.2, R2.3, R2.4, R2.5, R2.6, R2.7, R2.8 — see src/main/java/airhacks/zsmith/fm/package-info.java
/// The `fm` command is src/test/java/fm/FakeFm.java; it logs how it was launched and every turn it receives.

void main() throws IOException {
    ZCfg.loadBaseConfig("zsmith-test-fm-" + ProcessHandle.current().pid());
    var log = Files.createTempFile("zunit-fm", ".log");
    var unavailableLog = Files.createTempFile("zunit-fm-unavailable", ".log");
    var fm = Fm.of(fakeFm(log), Duration.ofSeconds(10));
    try {
        var answer = fm.invokeModel("Answer briefly.", conversation(), null, 0.3f, ToolChoice.auto);
        var request = lastRequest(log);
        var sent = request.getJSONArray("messages");

        // R2.1 — When a turn is sent, the BC shall answer the model's text, its stop reason, the name
        // of the model that served it and the tokens it used.
        var usage = answer.getJSONObject("usage");
        assert "echo: and tomorrow?".equals(answer.getJSONArray("content").getJSONObject(0).getString("text"))
                && "end_turn".equals(answer.getString("stop_reason"))
                && "system".equals(answer.getString("model"))
                && usage.getInt("input_tokens") == 65 && usage.getInt("output_tokens") == 3
                : "R2.1 — expected text, stop reason, model and tokens but got: " + answer;

        // R2.2 — The BC shall send the system prompt as the model's instructions.
        var instructions = sent.getJSONObject(0);
        assert "system".equals(instructions.getString("role")) && "Answer briefly.".equals(instructions.getString("content"))
                : "R2.2 — expected the system prompt as instructions but sent: " + sent;

        // R2.3 — The BC shall send the whole conversation, including earlier tool calls and their results.
        var call = sent.getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0);
        var result = sent.getJSONObject(3);
        assert sent.length() == 5
                && "weather in Munich?".equals(sent.getJSONObject(1).getString("content"))
                && "toolu_1".equals(call.getString("id")) && "get_weather".equals(call.getJSONObject("function").getString("name"))
                && "toolu_1".equals(result.getString("tool_call_id")) && "sunny, 21C".equals(result.getString("content"))
                && "and tomorrow?".equals(sent.getJSONObject(4).getString("content"))
                : "R2.3 — expected the whole conversation with tool call and result but sent: " + sent;

        // R2.4 — While no model is configured, the BC shall address the model named `system`.
        assert "system".equals(request.getString("model")) : "R2.4 — expected model `system` but sent: " + request;

        // R2.5 — While no output limit is configured, the BC shall limit a response to 4,096 output tokens.
        assert request.getInt("max_tokens") == 4096 : "R2.5 — expected a 4096 token limit but sent: " + request;

        // R2.6 — The BC shall send the temperature it is given.
        assert Math.abs(request.getDouble("temperature") - 0.3) < 0.0001
                : "R2.6 — expected temperature 0.3 but sent: " + request;

        // R2.8 — If the server answers a turn with an error, then the BC shall fail the turn with the
        // server's message and not send it again.
        var before = entries(log, "request").size();
        var failure = failureOf(() -> fm.invokeModel(null, user("fail"), null, 0.3f, ToolChoice.auto));
        assert failure != null && failure.contains("An unsupported generation guide was used.")
                : "R2.8 — expected the turn failed with the server's message but got: " + failure;
        assert entries(log, "request").size() == before + 1
                : "R2.8 — expected the failed turn sent once but the log shows: " + entries(log, "request");

        // R2.7 — If the model is unavailable, then the BC shall fail the turn with the reason and start no server.
        var unavailable = Fm.of(fakeFm(unavailableLog, "--unavailable"), Duration.ofSeconds(10));
        var reason = failureOf(() -> unavailable.invokeModel(null, user("hello"), null, 0.3f, ToolChoice.auto));
        assert reason != null && reason.contains("Apple Intelligence is not enabled.")
                : "R2.7 — expected the turn failed with the reason but got: " + reason;
        assert entries(unavailableLog, "serving").isEmpty()
                : "R2.7 — expected no server started but the log shows: " + Files.readAllLines(unavailableLog);
    } finally {
        fm.stopServer();
        Files.deleteIfExists(log);
        Files.deleteIfExists(unavailableLog);
    }
}

static JSONArray conversation() {
    return new JSONArray()
            .put(new JSONObject().put("role", "user").put("content", "weather in Munich?"))
            .put(new JSONObject().put("role", "assistant").put("content", new JSONArray().put(new JSONObject()
                    .put("type", "tool_use").put("id", "toolu_1").put("name", "get_weather")
                    .put("input", new JSONObject().put("city", "Munich")))))
            .put(new JSONObject().put("role", "user").put("content", new JSONArray().put(new JSONObject()
                    .put("type", "tool_result").put("tool_use_id", "toolu_1").put("content", "sunny, 21C"))))
            .put(new JSONObject().put("role", "user").put("content", "and tomorrow?"));
}

static JSONArray user(String text) {
    return new JSONArray().put(new JSONObject().put("role", "user").put("content", text));
}

static String failureOf(Runnable turn) {
    try {
        turn.run();
        return null;
    } catch (IllegalStateException e) {
        return e.getMessage();
    }
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
