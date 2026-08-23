import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.logging.boundary.LogSink;
import airhacks.zsmith.tools.boundary.Tool;

/// Traces agent spec R2.14 — see src/main/java/airhacks/zsmith/agent/package-info.java
///
/// The sub-agent is built first and the parent second, the order every agent script uses. If the
/// diagnostics keyed on construction the log would land under `helper`, so this fails on the
/// mistake the requirement exists to prevent — the same trap as the capture beside it.

static final String CHILD = "log-helper";
static final String PARENT = "log-lead";
static final AtomicInteger SERVED = new AtomicInteger();

static final List<String> SCRIPT = List.of(
        toolUse("toolu_1", "echo"),
        endTurn("the answer the user waits for"));

void main() throws Exception {
    var home = Files.createTempDirectory("zsmith-agent-logsink");
    System.setProperty("user.home", home.toString());
    System.setProperty(LogSink.SINK, "file");

    var server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/messages", this::handle);
    server.start();
    configureStubbedLLM(server.getAddress().getPort());
    try {
        directsDiagnosticsUnderTheAgentThatRuns(home);
    } finally {
        server.stop(0);
        LogSink.releaseSink();
        System.clearProperty(LogSink.SINK);
    }
}

// R2.14 — When a conversation loop begins, the BC shall direct its diagnostics under that agent's
// name.
void directsDiagnosticsUnderTheAgentThatRuns(Path home) throws Exception {
    var echo = Tool.of("echo", "Answers with a fixed token", _ -> "ok");

    // built first, never runs — the trap for a construction-order implementation
    new Agent(CHILD, "prompt").withTools(echo);
    var lead = new Agent(PARENT, "prompt").withTools(echo);

    var captured = new ByteArrayOutputStream();
    var original = System.out;
    System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
        lead.chat("go");
    } finally {
        System.setOut(original);
    }
    var printed = captured.toString(StandardCharsets.UTF_8);

    var zsmithHome = home.resolve(".zsmith");
    var childLogs = zsmithHome.resolve(CHILD).resolve("logs");
    if (Files.exists(childLogs))
        throw new AssertionError("R2.14 — diagnostics landed under the agent built first: " + childLogs);

    var logs = zsmithHome.resolve(PARENT).resolve("logs");
    if (!Files.isDirectory(logs))
        throw new AssertionError("R2.14 — the loop began but directed no diagnostics; expected " + logs);

    var written = Files.list(logs).findFirst()
            .orElseThrow(() -> new AssertionError("R2.14 — no log written under " + logs));
    if (!written.getFileName().toString().startsWith(PARENT))
        throw new AssertionError("R2.14 — expected the log named for the running agent, got " + written.getFileName());

    // the run's own reporting went to the file
    var logged = Files.readString(written);
    if (!logged.contains("loop end"))
        throw new AssertionError("R2.14 — the run's diagnostics are missing from " + written + ", holds: " + logged);
    if (printed.contains("loop end"))
        throw new AssertionError("R2.14 — diagnostics still reached the console: " + printed);

    // and what the user is owed stayed where they can read it
    if (!printed.contains("the answer the user waits for"))
        throw new AssertionError("R2.14 — the answer was redirected away from the user: " + printed);
}

void handle(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    var index = SERVED.getAndIncrement();
    var body = index < SCRIPT.size() ? SCRIPT.get(index) : endTurn("exhausted");
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (var out = exchange.getResponseBody()) {
        out.write(bytes);
    }
}

static String toolUse(String id, String toolName) {
    var content = new JSONArray().put(new JSONObject()
            .put("type", "tool_use").put("id", id).put("name", toolName).put("input", new JSONObject()));
    return new JSONObject().put("content", content).put("stop_reason", "tool_use").toString();
}

static String endTurn(String text) {
    var content = new JSONArray().put(new JSONObject().put("type", "text").put("text", text));
    return new JSONObject().put("content", content).put("stop_reason", "end_turn").toString();
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
