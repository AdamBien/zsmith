import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

/// Traces agent spec R3.3 — see src/main/java/airhacks/zsmith/agent/package-info.java
///
/// The confirm flow reads the answer from the process console, so the agent runs in a child
/// JVM with a piped stdin; the stubbed LLM stays in this parent process.

long pid = ProcessHandle.current().pid();
List<JSONObject> llmRequests = new CopyOnWriteArrayList<>();

void main() throws Exception {
    var stub = HttpServer.create(new InetSocketAddress(0), 0);
    stub.createContext("/v1/messages", this::handle);
    stub.start();
    var port = stub.getAddress().getPort();
    try {
        alwaysAnswerExecutesAndPersistsAllow(port);
        noAnswerRejectsExecution(port);
    } finally {
        stub.stop(0);
    }
}

void handle(HttpExchange exchange) throws IOException {
    var request = new JSONObject(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    this.llmRequests.add(request);
    var messages = request.getJSONArray("messages");
    var lastContent = messages.getJSONObject(messages.length() - 1).get("content");
    var toolResultTurn = lastContent instanceof JSONArray blocks
            && "tool_result".equals(blocks.getJSONObject(0).optString("type"));
    var body = toolResultTurn
            ? new JSONObject()
                    .put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", "child done")))
                    .put("stop_reason", "end_turn")
                    .toString()
            : new JSONObject()
                    .put("content", new JSONArray().put(new JSONObject()
                            .put("type", "tool_use")
                            .put("id", "tu-1")
                            .put("name", "confirm_tool")
                            .put("input", new JSONObject())))
                    .put("stop_reason", "tool_use")
                    .toString();
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (var out = exchange.getResponseBody()) {
        out.write(bytes);
    }
}

// R3.3 — While a tool's permission resolves to confirm, when the tool is requested, the BC
// shall ask the user before executing and persist an always or never answer.
void alwaysAnswerExecutesAndPersistsAllow(int port) throws Exception {
    var agentName = "confirm-always-" + this.pid;
    this.llmRequests.clear();
    var output = runChild(agentName, port, "always\n");
    try {
        if (!output.contains("Allow confirm_tool"))
            throw new AssertionError("R3.3 — expected the user to be asked before execution but child printed:\n" + output);
        if (!output.contains("TOOL_EXECUTED"))
            throw new AssertionError("R3.3 — expected tool execution after 'always' but child printed:\n" + output);
        var persisted = agentConfig(agentName);
        if (!persisted.contains("allow"))
            throw new AssertionError("R3.3 — expected persisted allow permission but config was: " + persisted);
    } finally {
        cleanupAgentDir(agentName);
    }
}

void noAnswerRejectsExecution(int port) throws Exception {
    var agentName = "confirm-no-" + this.pid;
    this.llmRequests.clear();
    var output = runChild(agentName, port, "no\n");
    try {
        if (!output.contains("Allow confirm_tool"))
            throw new AssertionError("R3.3 — expected the user to be asked before execution but child printed:\n" + output);
        if (output.contains("TOOL_EXECUTED"))
            throw new AssertionError("R3.3 — expected no tool execution after 'no' but child printed:\n" + output);
        var rejection = this.llmRequests.stream()
                .map(JSONObject::toString)
                .filter(request -> request.contains("Denied"))
                .findFirst();
        if (rejection.isEmpty())
            throw new AssertionError("R3.3 — expected a Denied tool_result reported to the LLM");
    } finally {
        cleanupAgentDir(agentName);
    }
}

String runChild(String agentName, int port, String stdin) throws Exception {
    // source-file mode derives the implicit class name from the file name, so it must be a
    // valid identifier — a hyphenated createTempFile name fails with "bad file name"
    var childDir = Files.createTempDirectory("agent-confirm");
    var childSource = childDir.resolve("AgentConfirmChild.java");
    Files.writeString(childSource, """
            import airhacks.zsmith.agent.boundary.Agent;
            import airhacks.zsmith.tools.control.ToolHandler;

            void main() {
                var agent = new Agent("%s", "confirm flow test")
                        .withTool(ToolHandler.of("confirm_tool", "asks before running", input -> {
                            IO.println("TOOL_EXECUTED");
                            return "ran";
                        }));
                IO.println("ANSWER:" + agent.chat("start"));
            }
            """.formatted(agentName));
    try {
        var process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                "-Dllm.provider=claude",
                "-Dclaude.model=claude-opus-4-8",
                "-Dclaude.scheme=http",
                "-Dclaude.host=localhost",
                "-Dclaude.port=" + port,
                "-Dclaude.path=/v1/messages",
                "-Danthropic.api.key=test-key",
                "-Danthropic.version=2023-06-01",
                "-Dtools.permissions.confirm_tool=confirm",
                childSource.toString())
                .redirectErrorStream(true)
                .start();
        try (var in = process.getOutputStream()) {
            in.write(stdin.getBytes(StandardCharsets.UTF_8));
        }
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("R3.3 — child agent did not finish, output so far:\n" + output);
        }
        return output;
    } finally {
        Files.deleteIfExists(childSource);
        Files.deleteIfExists(childDir);
    }
}

String agentConfig(String agentName) throws IOException {
    var configFile = Path.of(System.getProperty("user.home"), ".zsmith", agentName, "app.properties");
    return Files.exists(configFile) ? Files.readString(configFile) : "<missing>";
}

void cleanupAgentDir(String agentName) throws IOException {
    var dir = Path.of(System.getProperty("user.home"), ".zsmith", agentName);
    if (!Files.exists(dir))
        return;
    try (var paths = Files.walk(dir)) {
        paths.sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                throw new IllegalStateException("cannot delete " + path, e);
            }
        });
    }
}
