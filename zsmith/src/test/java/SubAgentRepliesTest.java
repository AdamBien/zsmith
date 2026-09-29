import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Queue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.subagent.control.SubAgentTool;
import airhacks.zsmith.tools.boundary.Tool;

/// A delegation returns everything the sub-agent wrote, not only its closing reply — traced
/// against a stubbed LLM endpoint.

record StubResponse(int status, String body) {}

Queue<StubResponse> script = new ArrayDeque<>();

void main() throws Exception {
    // the first-run marker is written below the home of the user
    System.setProperty("user.home", Files.createTempDirectory("zsmith-subagent-replies-test").toString());
    var server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/messages", this::handle);
    server.start();
    configureStubbedLLM(server.getAddress().getPort());
    try {
        resultWrittenBesideAToolCallIsReturned();
        closingReplyAloneIsReturnedUnchanged();
        laterDelegationReturnsItsOwnRepliesOnly();
        exhaustedLoopReportsWhatWasWritten();
    } finally {
        server.stop(0);
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

void handle(HttpExchange exchange) throws IOException {
    exchange.getRequestBody().readAllBytes();
    var response = this.script.size() > 1 ? this.script.poll() : this.script.peek();
    var bytes = response.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(response.status(), bytes.length);
    try (var out = exchange.getResponseBody()) {
        out.write(bytes);
    }
}

static String textTurn(String text) {
    return new JSONObject()
            .put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", text)))
            .put("stop_reason", "end_turn")
            .toString();
}

static String toolUseTurn(String toolName) {
    return new JSONObject()
            .put("content", new JSONArray().put(toolUse(toolName)))
            .put("stop_reason", "tool_use")
            .toString();
}

static String textBesideToolUseTurn(String text, String toolName) {
    return new JSONObject()
            .put("content", new JSONArray()
                    .put(new JSONObject().put("type", "text").put("text", text))
                    .put(toolUse(toolName)))
            .put("stop_reason", "tool_use")
            .toString();
}

static JSONObject toolUse(String toolName) {
    return new JSONObject()
            .put("type", "tool_use")
            .put("id", "tu-1")
            .put("name", toolName)
            .put("input", new JSONObject().put("value", "x"));
}

SubAgentTool delegation(String name, int maxIterations) {
    var child = new Agent(name, "prompt")
            .withTool(Tool.of("probe", "probes", input -> "probed"))
            .withMaxIterations(maxIterations);
    return new SubAgentTool(child);
}

static JSONObject task(String task) {
    return new JSONObject().put("task", task);
}

/// The result precedes a tool call, the closing turn only recaps it.
void resultWrittenBesideAToolCallIsReturned() {
    this.script.clear();
    this.script.add(new StubResponse(200, toolUseTurn("probe")));
    this.script.add(new StubResponse(200, textBesideToolUseTurn("PRIMARY: GitHub https://github.com/duke", "probe")));
    this.script.add(new StubResponse(200, textTurn("Research completed.")));

    var result = delegation("replies-result", 5).execute(task("research duke"));

    var expected = "PRIMARY: GitHub https://github.com/duke\n\nResearch completed.";
    if (!expected.equals(result))
        throw new AssertionError("expected the result followed by the closing reply but got: " + result);
}

void closingReplyAloneIsReturnedUnchanged() {
    this.script.clear();
    this.script.add(new StubResponse(200, toolUseTurn("probe")));
    this.script.add(new StubResponse(200, textTurn("STATUS: PASS")));

    var result = delegation("replies-closing", 5).execute(task("review"));

    if (!"STATUS: PASS".equals(result))
        throw new AssertionError("expected the closing reply unchanged but got: " + result);
}

/// The sub-agent keeps its conversation between delegations, the caller has seen the earlier one.
void laterDelegationReturnsItsOwnRepliesOnly() {
    var tool = delegation("replies-later", 5);
    this.script.clear();
    this.script.add(new StubResponse(200, textBesideToolUseTurn("first result", "probe")));
    this.script.add(new StubResponse(200, textTurn("first closing")));
    tool.execute(task("first"));

    this.script.clear();
    this.script.add(new StubResponse(200, textTurn("second closing")));
    var result = tool.execute(task("second"));

    if (!"second closing".equals(result))
        throw new AssertionError("expected the replies of the second delegation only but got: " + result);
}

/// The loop's verdict is no message of the conversation, what was written before it is.
void exhaustedLoopReportsWhatWasWritten() {
    this.script.clear();
    this.script.add(new StubResponse(200, textBesideToolUseTurn("partial result", "probe")));

    var result = delegation("replies-exhausted", 2).execute(task("loop"));

    var expected = "partial result\n\npartial result\n\nMax iterations reached";
    if (!expected.equals(result))
        throw new AssertionError("expected the written replies closed by the verdict but got: " + result);
}
