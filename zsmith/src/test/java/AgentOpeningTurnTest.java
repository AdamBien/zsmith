import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.tools.boundary.Tool;

/// Traces agent spec R2.15–R2.17 against a stubbed LLM endpoint —
/// see src/main/java/airhacks/zsmith/agent/package-info.java

record StubResponse(int status, String body) {}

Queue<StubResponse> script = new ArrayDeque<>();
List<JSONObject> requests = new CopyOnWriteArrayList<>();

void main() throws Exception {
    // the improvement log resolves its directory from the home of the user
    System.setProperty("user.home", Files.createTempDirectory("zsmith-opening-turn-test").toString());
    var server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/messages", this::handle);
    server.start();
    configureStubbedLLM(server.getAddress().getPort());
    try {
        openingTurnDemandsAToolCall();
        continuedConversationKeepsItsToolChoice();
        sideChannelIsNoOpeningMove();
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
    var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    this.requests.add(new JSONObject(body));
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
    var toolUse = new JSONObject()
            .put("type", "tool_use")
            .put("id", "tu-1")
            .put("name", toolName)
            .put("input", new JSONObject().put("value", "x"));
    return new JSONObject()
            .put("content", new JSONArray().put(toolUse))
            .put("stop_reason", "tool_use")
            .toString();
}

// R2.15 — When a conversation opens, the BC shall demand a tool call on the first turn and
// leave every later turn to the model.
void openingTurnDemandsAToolCall() {
    this.script.clear();
    this.requests.clear();
    this.script.add(new StubResponse(200, toolUseTurn("echo_tool")));
    this.script.add(new StubResponse(200, textTurn("done R2.15")));
    var agent = new Agent("opening-r215", "prompt")
            .withTool(Tool.of("echo_tool", "echoes input", input -> "echo"));
    agent.chat("open R2.15");

    var opening = this.requests.get(0);
    if (!"any".equals(toolChoiceOf(opening)))
        throw new AssertionError("R2.15 — expected a demanded tool call on the opening turn but got: " + opening.opt("tool_choice"));
    var second = this.requests.get(1);
    if (second.has("tool_choice"))
        throw new AssertionError("R2.15 — expected the second turn left to the model but got: " + second.opt("tool_choice"));
}

// R2.16 — While a conversation is continued, the BC shall leave the first turn of the next
// message to the model.
void continuedConversationKeepsItsToolChoice() {
    this.script.clear();
    this.requests.clear();
    this.script.add(new StubResponse(200, textTurn("answer")));
    var agent = new Agent("opening-r216", "prompt")
            .withTool(Tool.of("echo_tool", "echoes input", input -> "echo"));
    agent.chat("first message R2.16");
    agent.chat("second message R2.16");

    if (!"any".equals(toolChoiceOf(this.requests.get(0))))
        throw new AssertionError("R2.16 — expected the conversation to open with a demanded tool call");
    var continued = this.requests.get(1);
    if (continued.has("tool_choice"))
        throw new AssertionError("R2.16 — expected no tool choice on a continued conversation but got: " + continued.opt("tool_choice"));

    agent.clearMemory();
    agent.chat("fresh message R2.16");
    if (!"any".equals(toolChoiceOf(this.requests.get(2))))
        throw new AssertionError("R2.16 — expected a cleared conversation to open like a new one");
}

// R2.17 — While the improvement report is the only registered tool, the BC shall leave the
// first turn to the model.
void sideChannelIsNoOpeningMove() {
    this.script.clear();
    this.requests.clear();
    this.script.add(new StubResponse(200, textTurn("answer R2.17")));
    var agent = new Agent("opening-r217", "prompt")
            .withImprovementLog();
    var answer = agent.chat("open R2.17");

    var opening = this.requests.getFirst();
    if (opening.has("tool_choice"))
        throw new AssertionError("R2.17 — expected no demanded tool call but got: " + opening.opt("tool_choice"));
    if (!"answer R2.17".equals(answer))
        throw new AssertionError("R2.17 — expected the answer on the first turn but got: " + answer);
    if (this.requests.size() != 1)
        throw new AssertionError("R2.17 — expected a single LLM invocation but got: " + this.requests.size());

    this.requests.clear();
    var equipped = new Agent("opening-r217-equipped", "prompt")
            .withTool(Tool.of("echo_tool", "echoes input", input -> "echo"))
            .withImprovementLog();
    equipped.chat("open R2.17");
    if (!"any".equals(toolChoiceOf(this.requests.getFirst())))
        throw new AssertionError("R2.17 — expected the demand to stay once a working tool is registered");
}

static String toolChoiceOf(JSONObject request) {
    var toolChoice = request.optJSONObject("tool_choice");
    return toolChoice == null ? null : toolChoice.optString("type", null);
}
