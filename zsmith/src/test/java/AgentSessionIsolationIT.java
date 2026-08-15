import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.agent.boundary.Agent;

/// Traces agent spec R5.1 — see src/main/java/airhacks/zsmith/agent/package-info.java

List<JSONObject> llmRequests = new CopyOnWriteArrayList<>();
AtomicInteger replies = new AtomicInteger();

void main() throws Exception {
    var stub = HttpServer.create(new InetSocketAddress(0), 0);
    stub.createContext("/v1/messages", this::handle);
    stub.start();
    configureStubbedLLM(stub.getAddress().getPort());
    try {
        sessionsGetIsolatedMemory();
    } catch (Throwable failure) {
        failure.printStackTrace();
        System.exit(1);
    } finally {
        stub.stop(0);
    }
    // withHttpServer keeps non-daemon server threads alive by design (long-running server
    // scripts), so the IT must exit explicitly or the JVM never terminates
    System.exit(0);
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
    var request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    this.llmRequests.add(new JSONObject(request));
    var body = new JSONObject()
            .put("content", new JSONArray().put(new JSONObject()
                    .put("type", "text")
                    .put("text", "reply-" + this.replies.getAndIncrement())))
            .put("stop_reason", "end_turn")
            .toString();
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (var out = exchange.getResponseBody()) {
        out.write(bytes);
    }
}

// R5.1 — When serving over HTTP, the BC shall give each session an isolated agent clone with
// its own conversation memory and shared tools and configuration.
void sessionsGetIsolatedMemory() throws Exception {
    int port;
    try (var probe = new ServerSocket(0)) {
        port = probe.getLocalPort();
    }
    new Agent("session-agent", "prompt").withHttpServer(port);

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
        chat(client, port, "alpha", "a1");
        chat(client, port, "alpha", "a2");
        chat(client, port, "beta", "b1");
    }

    var alphaFollowUp = requestWithLastMessage("a2");
    var contents = messageContents(alphaFollowUp);
    if (!contents.contains("a1"))
        throw new AssertionError("R5.1 — expected session alpha to keep its own history but got: " + contents);
    if (contents.contains("b1"))
        throw new AssertionError("R5.1 — expected session alpha isolated from beta but got: " + contents);

    var betaFirst = requestWithLastMessage("b1");
    if (betaFirst.getJSONArray("messages").length() != 1)
        throw new AssertionError("R5.1 — expected a fresh memory for session beta but got: "
                + betaFirst.getJSONArray("messages"));
}

void chat(HttpClient client, int port, String sessionId, String message) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/chat"))
            .timeout(Duration.ofSeconds(10))
            .header("X-Session-Id", sessionId)
            .POST(BodyPublishers.ofString(message))
            .build();
    var response = client.send(request, BodyHandlers.ofString());
    if (response.statusCode() != 200)
        throw new AssertionError("R5.1 — test setup expected 200 from /chat but got: " + response.statusCode());
}

JSONObject requestWithLastMessage(String content) {
    return this.llmRequests.stream()
            .filter(request -> {
                var messages = request.getJSONArray("messages");
                var last = messages.getJSONObject(messages.length() - 1);
                return content.equals(text(last.opt("content")));
            })
            .findFirst()
            .orElseThrow(() -> new AssertionError("R5.1 — no LLM request ends with message: " + content));
}

String messageContents(JSONObject request) {
    var messages = request.getJSONArray("messages");
    var all = new StringBuilder();
    for (int i = 0; i < messages.length(); i++) {
        all.append(text(messages.getJSONObject(i).opt("content"))).append('\n');
    }
    return all.toString();
}

/// The wire carries message content either as a plain string or as an array of typed
/// text blocks (prompt caching normalizes to block form); both flatten to their text.
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
