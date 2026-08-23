import java.io.IOException;
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
import airhacks.zsmith.telemetry.boundary.EventCapture;
import airhacks.zsmith.telemetry.boundary.EventLog;
import airhacks.zsmith.tools.boundary.Tool;

/// Traces agent spec R2.13 — see src/main/java/airhacks/zsmith/agent/package-info.java
///
/// The sub-agent is built first and the parent second, which is the order every real agent script
/// uses — a child has to exist before the parent that delegates to it. If capture keyed on
/// construction the recording would land under `helper`, so this fails loudly on the mistake the
/// requirement exists to prevent.

static final String CHILD = "capture-helper";
static final String PARENT = "capture-lead";
static final AtomicInteger SERVED = new AtomicInteger();

static final List<String> SCRIPT = List.of(
        toolUse("toolu_1", "echo"),
        endTurn("done"));

void main() throws Exception {
    var home = Files.createTempDirectory("zsmith-agent-capture");
    System.setProperty("user.home", home.toString());
    System.setProperty(EventCapture.ENABLED, "true");

    var server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/messages", this::handle);
    server.start();
    configureStubbedLLM(server.getAddress().getPort());
    try {
        capturesUnderTheAgentThatRuns(home);
    } finally {
        server.stop(0);
        System.clearProperty(EventCapture.ENABLED);
    }
}

// R2.13 — When a conversation loop begins, the BC shall request event capture under that agent's
// name.
void capturesUnderTheAgentThatRuns(Path home) throws Exception {
    var echo = Tool.of("echo", "Answers with a fixed token", _ -> "ok");

    // built first, never runs — the trap for a construction-order implementation
    new Agent(CHILD, "prompt").withTools(echo);
    var lead = new Agent(PARENT, "prompt").withTools(echo);

    lead.chat("go");

    var written = EventCapture.stopRecording()
            .orElseThrow(() -> new AssertionError("R2.13 — the loop began but asked for no capture"));

    var zsmithHome = home.resolve(".zsmith");
    if (written.startsWith(zsmithHome.resolve(CHILD)))
        throw new AssertionError("R2.13 — capture landed under the agent built first, not the one that ran: "
                + written);
    if (!written.startsWith(zsmithHome.resolve(PARENT)))
        throw new AssertionError("R2.13 — expected the capture under " + zsmithHome.resolve(PARENT)
                + " but got " + written);
    if (!Files.exists(written) || Files.size(written) == 0)
        throw new AssertionError("R2.13 — no capture was written to " + written);

    // and it holds the run it was opened for
    var reports = EventLog.replay(written);
    var ran = reports.values().stream().anyMatch(report -> PARENT.equals(report.agent()));
    if (!ran)
        throw new AssertionError("R2.13 — the capture holds no run of " + PARENT + ", only: "
                + reports.values().stream().map(r -> r.agent()).toList());
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
