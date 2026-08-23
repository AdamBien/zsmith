import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
import airhacks.zsmith.telemetry.boundary.RunTally;
import airhacks.zsmith.tools.boundary.Tool;

/// Traces agent spec R2.11 and R2.12 — see src/main/java/airhacks/zsmith/agent/package-info.java
///
/// Reads the display the loop actually printed rather than calling the renderer directly: the
/// requirement is that a run shows what it is spending while it runs, and a renderer asked for a
/// string in isolation would satisfy that whether or not the loop ever calls it.

static final AtomicInteger SERVED = new AtomicInteger();

/// Three scripted calls, each reporting 10 in / 4 out / 3 cache-read / 2 cache-create.
static final List<String> SCRIPT = List.of(
        toolUse("toolu_1", "echo"),
        toolUse("toolu_2", "echo"),
        endTurn("done"));

void main() throws Exception {
    var server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/messages", this::handle);
    server.start();
    configureStubbedLLM(server.getAddress().getPort());
    try {
        var run = runAgentCapturingOutput();
        displaysTheRunningSpend(run.output());
        releasesTheTallyWhenTheLoopEnds(run.runId());
    } finally {
        server.stop(0);
    }
}

record Run(String output, String runId) {
}

Run runAgentCapturingOutput() throws Exception {
    var observedRunId = new AtomicReference<String>();
    var captured = new ByteArrayOutputStream();
    var original = System.out;

    try (var stream = new RecordingStream()) {
        stream.enable(AgentTurnEvent.NAME);
        stream.onEvent(AgentTurnEvent.NAME, event -> observedRunId.compareAndSet(null, event.getString("runId")));
        stream.startAsync();

        var echo = Tool.of("echo", "Answers with a fixed token", _ -> "ok");
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            new Agent("token-progress-agent", "prompt").withTools(echo).chat("go");
        } finally {
            System.setOut(original);
        }
        stream.awaitTermination(Duration.ofMillis(1500));
    }

    var runId = observedRunId.get();
    if (runId == null || runId.isBlank())
        throw new AssertionError("the run never announced its identifier, so R2.12 cannot be judged");
    return new Run(captured.toString(StandardCharsets.UTF_8), runId);
}

// R2.11 — When a turn's LLM call reports token usage, the BC shall display the run's accumulated
// input and output counts alongside that turn's progress, and the accumulated total when the loop
// ends.
void displaysTheRunningSpend(String printed) {
    // the first turn already carries its own call's usage rather than a turn of lag
    assertShows(printed, "tok: 10↑ 4↓", "R2.11 — the first turn should show its own spend");
    // and every later turn shows the accumulation, not just that one call
    assertShows(printed, "tok: 20↑ 8↓", "R2.11 — the second turn should accumulate");
    assertShows(printed, "tok: 30↑ 12↓", "R2.11 — the third turn should accumulate");

    // the closing total folds in the cache counts the per-turn line leaves out: (10+4+3+2)*3
    assertShows(printed, "tokens: in=30 out=12 total=57", "R2.11 — the summary should carry the run's totals");
}

// R2.12 — When the loop ends, the BC shall discard the run's tally.
void releasesTheTallyWhenTheLoopEnds(String runId) {
    var lingering = RunTally.runningTokens(runId);
    if (lingering.total() != 0)
        throw new AssertionError("R2.12 — the finished run still holds a tally of " + lingering);
}

void assertShows(String printed, String expected, String message) {
    if (!printed.contains(expected))
        throw new AssertionError("%s — expected '%s' in:%n%s".formatted(message, expected, printed));
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
    return new JSONObject().put("content", content).put("stop_reason", "tool_use").put("usage", usage()).toString();
}

static String endTurn(String text) {
    var content = new JSONArray().put(new JSONObject().put("type", "text").put("text", text));
    return new JSONObject().put("content", content).put("stop_reason", "end_turn").put("usage", usage()).toString();
}

static JSONObject usage() {
    return new JSONObject()
            .put("input_tokens", 10)
            .put("output_tokens", 4)
            .put("cache_read_input_tokens", 3)
            .put("cache_creation_input_tokens", 2);
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
