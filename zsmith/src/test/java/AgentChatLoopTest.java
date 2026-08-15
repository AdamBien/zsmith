import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.tools.control.ToolHandler;

/// Traces agent spec R2.1–R2.7 and R4.1 against a stubbed LLM endpoint —
/// see src/main/java/airhacks/zsmith/agent/package-info.java

record StubResponse(int status, String body) {}

Queue<StubResponse> script = new ArrayDeque<>();
List<JSONObject> requests = new CopyOnWriteArrayList<>();

void main() throws Exception {
    var server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/messages", this::handle);
    server.start();
    configureStubbedLLM(server.getAddress().getPort());
    try {
        userMessageReachesLLMWithToolDefinitions();
        toolLoopExecutesAndFeedsResults();
        answerTable();
        nullMessageRejected();
        actSendsGoTrigger();
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

// R2.1 — When a user message is submitted, the BC shall record it in conversation memory and
// invoke the LLM with the system prompt, the conversation, and all registered tool definitions.
// R4.1 — When a tool is registered, the BC shall expose its definition to the LLM on every invocation.
void userMessageReachesLLMWithToolDefinitions() {
    this.script.clear();
    this.requests.clear();
    this.script.add(new StubResponse(200, textTurn("ok")));
    var agent = new Agent("chatloop-r21", "system prompt marker R2.1")
            .withTool(ToolHandler.of("echo_tool", "echoes input", input -> "echo"));
    agent.chat("hello R2.1");

    var request = this.requests.getFirst();
    if (!text(request.opt("system")).contains("system prompt marker R2.1"))
        throw new AssertionError("R2.1 — expected system prompt in LLM request but got: " + request.opt("system"));
    var messages = request.getJSONArray("messages");
    var last = messages.getJSONObject(messages.length() - 1);
    if (!"hello R2.1".equals(text(last.opt("content"))))
        throw new AssertionError("R2.1 — expected user message in LLM request but got: " + last);
    var recorded = agent.memory().toJSON().getJSONObject(0);
    if (!"user".equals(recorded.getString("role")) || !"hello R2.1".equals(recorded.getString("content")))
        throw new AssertionError("R2.1 — expected user message recorded in memory but got: " + recorded);

    var tools = request.optJSONArray("tools");
    var found = false;
    for (int i = 0; tools != null && i < tools.length(); i++) {
        found |= "echo_tool".equals(tools.getJSONObject(i).getString("name"));
    }
    if (!found)
        throw new AssertionError("R4.1 — expected echo_tool definition in LLM request but got: " + tools);
}

// R2.2 — While the LLM requests tool use, the BC shall execute the requested tools, record the
// results in conversation memory, and re-invoke the LLM.
void toolLoopExecutesAndFeedsResults() {
    this.script.clear();
    this.requests.clear();
    this.script.add(new StubResponse(200, toolUseTurn("echo_tool")));
    this.script.add(new StubResponse(200, textTurn("done R2.2")));
    var executed = new AtomicReference<String>();
    var agent = new Agent("chatloop-r22", "prompt")
            .withTool(ToolHandler.of("echo_tool", "echoes input", input -> {
                executed.set(input.optString("value"));
                return "echoed:" + input.optString("value");
            }));
    var answer = agent.chat("run the tool");

    if (!"x".equals(executed.get()))
        throw new AssertionError("R2.2 — expected tool executed with input value 'x' but got: " + executed.get());
    if (this.requests.size() != 2)
        throw new AssertionError("R2.2 — expected LLM re-invocation after tool results but request count=" + this.requests.size());
    var followUp = this.requests.get(1).getJSONArray("messages");
    var toolResultMessage = followUp.getJSONObject(followUp.length() - 1);
    var block = toolResultMessage.getJSONArray("content").getJSONObject(0);
    if (!"tool_result".equals(block.getString("type")) || !"tu-1".equals(block.getString("tool_use_id")))
        throw new AssertionError("R2.2 — expected tool_result for tu-1 recorded in conversation but got: " + block);
    if (!"echoed:x".equals(block.getString("content")))
        throw new AssertionError("R2.2 — expected tool output fed back to the LLM but got: " + block);
    if (!"done R2.2".equals(answer))
        throw new AssertionError("R2.2 — expected final answer after tool loop but got: " + answer);
}

// R2.3 — When the LLM responds without requesting tools, the BC shall record and return the
//        assistant text as the final answer.
// R2.4 — If the iteration limit is reached before a final answer, then the BC shall stop the
//        loop and report that the limit was reached.
// R2.5 — If the conversation loop fails, then the BC shall return a summarized error instead
//        of propagating the failure.
void answerTable() {
    record Case(String req, List<StubResponse> responses, int maxIterations, String expectedFragment, int expectedRequests) {}
    var cases = List.of(
            new Case("R2.3", List.of(new StubResponse(200, textTurn("final answer R2.3"))), 5, "final answer R2.3", 1),
            new Case("R2.4", List.of(new StubResponse(200, toolUseTurn("looping_tool"))), 2, "Max iterations reached", 2),
            new Case("R2.5", List.of(new StubResponse(400, "{\"error\":\"bad request R2.5\"}")), 5, "claude API error 400", 1));

    for (var c : cases) {
        this.script.clear();
        this.requests.clear();
        this.script.addAll(c.responses());
        var agent = new Agent("chatloop-" + c.req(), "prompt")
                .withTool(ToolHandler.of("looping_tool", "loops forever", input -> "again"))
                .withMaxIterations(c.maxIterations());
        var answer = agent.chat("question " + c.req());
        if (answer == null || !answer.contains(c.expectedFragment()))
            throw new AssertionError("%s — expected answer containing '%s' but got: %s"
                    .formatted(c.req(), c.expectedFragment(), answer));
        if (this.requests.size() != c.expectedRequests())
            throw new AssertionError("%s — expected %d LLM invocations but got: %d"
                    .formatted(c.req(), c.expectedRequests(), this.requests.size()));
    }
}

// R2.6 — If a chat is requested without a message, then the BC shall reject the request.
void nullMessageRejected() {
    var agent = new Agent("chatloop-r26", "prompt");
    try {
        agent.chat(null);
        throw new AssertionError("R2.6 — expected rejection of null message but chat returned normally");
    } catch (NullPointerException expected) {
        // rejected as specified
    }
}

// R2.7 — When acting without a user message, the BC shall run the conversation with a generic go trigger.
void actSendsGoTrigger() {
    this.script.clear();
    this.requests.clear();
    this.script.add(new StubResponse(200, textTurn("acted")));
    var agent = new Agent("chatloop-r27", "prompt");
    agent.act();
    var messages = this.requests.getFirst().getJSONArray("messages");
    if (!"go".equals(text(messages.getJSONObject(0).opt("content"))))
        throw new AssertionError("R2.7 — expected 'go' trigger message but got: " + messages.getJSONObject(0));
}

/// The wire carries prompts either as plain strings or as arrays of typed text blocks
/// (prompt caching normalizes to block form); both flatten to their text.
static String text(Object value) {
    if (value instanceof String plain)
        return plain;
    if (!(value instanceof JSONArray blocks))
        return "";
    var text = new StringBuilder();
    for (int i = 0; i < blocks.length(); i++) {
        var block = blocks.optJSONObject(i);
        if (block != null && "text".equals(block.optString("type")))
            text.append(block.optString("text"));
    }
    return text.toString();
}
