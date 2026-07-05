import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.json.JSONArray;
import org.json.JSONObject;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.tools.control.ToolHandler;

/// Traces agent spec R3.1, R3.2, R3.4, R3.5 against a stubbed LLM endpoint —
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
        governanceTable();
        parallelAndSequentialExecution();
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
    System.setProperty("tools.permissions.denied_tool", "deny");
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

static JSONObject toolUseBlock(String id, String toolName) {
    return new JSONObject()
            .put("type", "tool_use")
            .put("id", id)
            .put("name", toolName)
            .put("input", new JSONObject());
}

static String toolUseTurn(JSONObject... blocks) {
    var content = new JSONArray();
    for (var block : blocks) {
        content.put(block);
    }
    return new JSONObject().put("content", content).put("stop_reason", "tool_use").toString();
}

JSONObject firstToolResult(JSONObject followUpRequest) {
    var messages = followUpRequest.getJSONArray("messages");
    var last = messages.getJSONObject(messages.length() - 1);
    return last.getJSONArray("content").getJSONObject(0);
}

// R3.1 — If a requested tool is not registered, then the BC shall answer the request with an error result.
// R3.2 — If a tool's permission resolves to deny, then the BC shall refuse execution with an error result.
// R3.4 — If a tool execution fails, then the BC shall convert the failure into an error result.
void governanceTable() {
    var executed = new AtomicBoolean();
    record Case(String req, String requestedTool, ToolHandler registered, String expectedFragment, boolean expectExecuted) {}
    var cases = List.of(
            new Case("R3.1", "ghost_tool",
                    ToolHandler.of("working_tool", "works", input -> {
                        executed.set(true);
                        return "worked";
                    }),
                    "not available", false),
            new Case("R3.2", "denied_tool",
                    ToolHandler.of("denied_tool", "configured deny", input -> {
                        executed.set(true);
                        return "should never run";
                    }),
                    "Denied", false),
            new Case("R3.4", "explosive_tool",
                    ToolHandler.of("explosive_tool", "throws", input -> {
                        executed.set(true);
                        throw new RuntimeException("kaboom R3.4");
                    }),
                    "kaboom R3.4", true));

    for (var c : cases) {
        this.script.clear();
        this.requests.clear();
        executed.set(false);
        this.script.add(new StubResponse(200, toolUseTurn(toolUseBlock("tu-1", c.requestedTool()))));
        this.script.add(new StubResponse(200, textTurn("finished")));
        var agent = new Agent("governance-" + c.req(), "prompt").withTool(c.registered());
        agent.chat("trigger " + c.req());

        var block = firstToolResult(this.requests.get(1));
        if (!block.optBoolean("is_error"))
            throw new AssertionError(c.req() + " — expected an error tool_result but got: " + block);
        if (!block.getString("content").contains(c.expectedFragment()))
            throw new AssertionError("%s — expected error containing '%s' but got: %s"
                    .formatted(c.req(), c.expectedFragment(), block.getString("content")));
        if (executed.get() != c.expectExecuted())
            throw new AssertionError("%s — expected tool execution=%s but was %s"
                    .formatted(c.req(), c.expectExecuted(), executed.get()));
    }
}

// R3.5 — When several parallel-capable tools are requested in one turn, the BC shall execute
// them concurrently and the remaining tools sequentially.
void parallelAndSequentialExecution() {
    this.script.clear();
    this.requests.clear();
    var virtualByTool = new ConcurrentHashMap<String, Boolean>();
    var parallelOne = ToolHandler.of("parallel_one", "parallel capable", ToolHandler.emptySchema(), input -> {
        virtualByTool.put("parallel_one", Thread.currentThread().isVirtual());
        return "p1";
    }, true);
    var parallelTwo = ToolHandler.of("parallel_two", "parallel capable", ToolHandler.emptySchema(), input -> {
        virtualByTool.put("parallel_two", Thread.currentThread().isVirtual());
        return "p2";
    }, true);
    var sequentialOne = ToolHandler.of("sequential_one", "sequential", input -> {
        virtualByTool.put("sequential_one", Thread.currentThread().isVirtual());
        return "s1";
    });

    this.script.add(new StubResponse(200, toolUseTurn(
            toolUseBlock("tu-a", "parallel_one"),
            toolUseBlock("tu-b", "parallel_two"),
            toolUseBlock("tu-c", "sequential_one"))));
    this.script.add(new StubResponse(200, textTurn("done")));
    var agent = new Agent("governance-r35", "prompt").withTools(parallelOne, parallelTwo, sequentialOne);
    agent.chat("run all R3.5");

    if (!Boolean.TRUE.equals(virtualByTool.get("parallel_one")) || !Boolean.TRUE.equals(virtualByTool.get("parallel_two")))
        throw new AssertionError("R3.5 — expected parallel-capable tools on concurrent worker threads but got: " + virtualByTool);
    if (!Boolean.FALSE.equals(virtualByTool.get("sequential_one")))
        throw new AssertionError("R3.5 — expected sequential tool on the caller thread but got: " + virtualByTool);
    var followUp = this.requests.get(1).getJSONArray("messages");
    var results = followUp.getJSONObject(followUp.length() - 1).getJSONArray("content");
    if (results.length() != 3)
        throw new AssertionError("R3.5 — expected all 3 tool results returned to the LLM but got: " + results);
}
