import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jdk.jfr.Recording;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.agent.entity.AgentTurnEvent;
import airhacks.zsmith.claude.entity.ClaudeAPICallEvent;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.telemetry.boundary.EventLog;
import airhacks.zsmith.tools.boundary.Tool;
import airhacks.zsmith.tools.entity.ToolInvocationEvent;

/// Scores a real agent run from a written recording — the path benchmark scoring would take.
///
/// File replay rather than a live stream on purpose: a finished file is seen whole, so the
/// numbers are the same every time it is read. A live stream would only see what was flushed
/// while it happened to be running.

final class Responses {

    static final AtomicInteger SERVED = new AtomicInteger();

    static final List<String> SCRIPT = List.of(
            toolUse("toolu_ok", "echo", new JSONObject()),
            toolUse("toolu_boom", "detonate", new JSONObject()),
            endTurn("done"));

    static String toolUse(String id, String toolName, JSONObject input) {
        var content = new JSONArray().put(new JSONObject()
                .put("type", "tool_use").put("id", id).put("name", toolName).put("input", input));
        return new JSONObject()
                .put("content", content)
                .put("stop_reason", "tool_use")
                .put("usage", usage())
                .toString();
    }

    static String endTurn(String text) {
        return new JSONObject()
                .put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", text)))
                .put("stop_reason", "end_turn")
                .put("usage", usage())
                .toString();
    }

    static JSONObject usage() {
        return new JSONObject()
                .put("input_tokens", 10)
                .put("output_tokens", 4)
                .put("cache_read_input_tokens", 3)
                .put("cache_creation_input_tokens", 2);
    }
}

void main() throws Exception {
    var server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/messages", this::handle);
    server.start();
    configureStubbedLLM(server.getAddress().getPort());

    var recordingFile = Files.createTempFile("zsmith-eventlog", ".jfr");
    try {
        var agentName = scoresARecordedRun(recordingFile);
        assertReport(recordingFile, agentName);
    } finally {
        server.stop(0);
        Files.deleteIfExists(recordingFile);
    }
}

String scoresARecordedRun(java.nio.file.Path recordingFile) throws Exception {
    var agentName = "scored-agent";
    var echo = Tool.of("echo", "Answers with a fixed token", _ -> "ok");
    var detonate = Tool.of("detonate", "Always fails", _ -> {
        throw new IllegalStateException("boom");
    });

    try (var recording = new Recording()) {
        recording.enable(AgentTurnEvent.NAME);
        recording.enable(ToolInvocationEvent.NAME);
        recording.enable(ClaudeAPICallEvent.NAME);
        recording.start();
        new Agent(agentName, "prompt").withTools(echo, detonate).chat("go");
        recording.stop();
        recording.dump(recordingFile);
    }
    return agentName;
}

void assertReport(java.nio.file.Path recordingFile, String agentName) {
    var reports = EventLog.replay(recordingFile);
    if (reports.size() != 1)
        throw new AssertionError("expected one run in the recording but got: " + reports.keySet());

    var report = reports.values().iterator().next();
    if (!agentName.equals(report.agent()))
        throw new AssertionError("the run should name its agent, got: " + report.summary());
    if (report.turns() != 3)
        throw new AssertionError("expected 3 turns, got: " + report.summary());
    if (report.toolCalls() != 2 || report.toolFailures() != 1)
        throw new AssertionError("expected 2 tool calls of which 1 failed, got: " + report.summary());
    // the failure is keyed by what went wrong, which is the point of carrying the type at all
    if (!java.util.Map.of("IllegalStateException", 1).equals(report.failures()))
        throw new AssertionError("expected the thrown type as the failure kind, got: " + report.summary());
    if (report.apiCalls() != 3 || report.retries() != 0)
        throw new AssertionError("expected 3 API calls and no retries, got: " + report.summary());
    if (!report.terminal() || report.incomplete())
        throw new AssertionError("a run that ended on end_turn is complete, got: " + report.summary());

    var tokens = report.tokens();
    if (tokens.input() != 30 || tokens.output() != 12 || tokens.cacheRead() != 9 || tokens.cacheCreation() != 6)
        throw new AssertionError("token usage should sum over the calls, got: " + tokens);
    if (report.depth() != 0 || report.subAgentDispatches() != 0)
        throw new AssertionError("a top-level run delegates to nobody, got: " + report.summary());
}

void handle(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    var index = Responses.SERVED.getAndIncrement();
    var body = index < Responses.SCRIPT.size() ? Responses.SCRIPT.get(index) : Responses.endTurn("exhausted");
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (var out = exchange.getResponseBody()) {
        out.write(bytes);
    }
}

void configureStubbedLLM(int port) {
    System.setProperty("llm.provider", "claude");
    System.setProperty("claude.model", "claude-opus-4-8");
    System.setProperty("claude.scheme", "http");
    System.setProperty("claude.host", "localhost");
    System.setProperty("claude.port", String.valueOf(port));
    System.setProperty("claude.path", "/v1/messages");
    System.setProperty("anthropic.api.key", "test-key");
    System.setProperty("anthropic.version", "2023-06-01");
    System.setProperty("tools.permissions.default", "allow");
}
