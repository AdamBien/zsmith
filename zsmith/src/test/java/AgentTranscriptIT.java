import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jdk.jfr.consumer.RecordingStream;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.agent.entity.AgentTurnEvent;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.transcripts.boundary.TranscriptLog;
import airhacks.zsmith.tools.boundary.Tool;

/// Traces agent spec R2.10 — see src/main/java/airhacks/zsmith/agent/package-info.java
///
/// The claim check has two halves and each is useless alone: the events carry the run id, the
/// transcript carries the content. This asserts the join actually holds — that the conversation
/// lands under the very id the run's turns announced, not merely that something was written.

static final String AGENT = "transcript-agent";
static final AtomicInteger SERVED = new AtomicInteger();

static final List<String> SCRIPT = List.of(
        toolUse("toolu_1", "echo"),
        endTurn("all done"));

void main() throws Exception {
    var home = Files.createTempDirectory("zsmith-transcript");
    var originalHome = System.getProperty("user.home");
    System.setProperty("user.home", home.toString());
    // must precede the first Agent: the configuration cache snapshots system properties on load
    System.setProperty(TranscriptLog.ENABLED, "true");

    var server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/messages", this::handle);
    server.start();
    configureStubbedLLM(server.getAddress().getPort());
    try {
        storesTheConversationUnderTheRunIdentifier();
    } finally {
        server.stop(0);
        System.setProperty("user.home", originalHome);
        System.clearProperty(TranscriptLog.ENABLED);
    }
}

// R2.10 — Where transcript storage is enabled, the BC shall store the conversation under the run
// identifier when the loop ends.
void storesTheConversationUnderTheRunIdentifier() throws Exception {
    var observedRunId = new AtomicReference<String>();

    try (var stream = new RecordingStream()) {
        stream.enable(AgentTurnEvent.NAME);
        stream.onEvent(AgentTurnEvent.NAME, event -> observedRunId.compareAndSet(null, event.getString("runId")));
        stream.startAsync();

        var echo = Tool.of("echo", "Answers with a fixed token", _ -> "ok");
        new Agent(AGENT, "prompt").withTools(echo).chat("remember this");
        stream.awaitTermination(Duration.ofMillis(1500));
    }

    var runId = observedRunId.get();
    if (runId == null || runId.isBlank())
        throw new AssertionError("R2.10 — the run announced no identifier to store the transcript under");

    var stored = TranscriptLog.forAgent(AGENT).read(runId);
    if (stored.isEmpty())
        throw new AssertionError("R2.10 — no transcript stored under the run id " + runId
                + ", found: " + TranscriptLog.forAgent(AGENT).runIds());

    var transcript = stored.orElseThrow();
    if (!runId.equals(transcript.runId()))
        throw new AssertionError("R2.10 — the transcript must be keyed by the run, got: " + transcript.runId());
    if (!AGENT.equals(transcript.agent()))
        throw new AssertionError("R2.10 — the transcript should name its agent, got: " + transcript.agent());
    if (transcript.turns() != 2)
        throw new AssertionError("R2.10 — expected the 2 turns of the loop, got: " + transcript.turns());
    if (!"end_turn".equals(transcript.outcome()))
        throw new AssertionError("R2.10 — expected the loop's exit reason, got: " + transcript.outcome());
    // the content is the half the events deliberately do not carry
    if (!transcript.conversation().contains("remember this"))
        throw new AssertionError("R2.10 — the stored conversation should carry what was said, got: "
                + transcript.conversation());
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
