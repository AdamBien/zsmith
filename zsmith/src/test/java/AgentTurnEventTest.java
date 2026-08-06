import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

import airhacks.zsmith.agent.boundary.Agent;

/// Traces agent spec R2.8 — see src/main/java/airhacks/zsmith/agent/package-info.java

void main() throws Exception {
    var server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/messages", this::handle);
    server.start();
    configureStubbedLLM(server.getAddress().getPort());
    try {
        turnEventEmittedPerIteration();
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
    var body = new JSONObject()
            .put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", "ok")))
            .put("stop_reason", "end_turn")
            .toString();
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (var out = exchange.getResponseBody()) {
        out.write(bytes);
    }
}

// R2.8 — The BC shall emit an observable turn event for every loop iteration.
void turnEventEmittedPerIteration() throws Exception {
    var latch = new CountDownLatch(1);
    var recorded = new AtomicReference<RecordedEvent>();
    try (var stream = new RecordingStream()) {
        stream.enable("airhacks.zsmith.agent.Turn");
        stream.onEvent("airhacks.zsmith.agent.Turn", event -> {
            recorded.set(event);
            latch.countDown();
        });
        stream.startAsync();
        Thread.sleep(1_000);

        var agent = new Agent("turn-event-agent", "prompt");
        agent.chat("emit R2.8");

        if (!latch.await(10, TimeUnit.SECONDS))
            throw new AssertionError("R2.8 — expected a turn event per loop iteration but none was recorded");
        var event = recorded.get();
        if (!"turn-event-agent".equals(event.getString("agentName")))
            throw new AssertionError("R2.8 — expected turn event for the agent but got: " + event);
        if (!event.getBoolean("terminal"))
            throw new AssertionError("R2.8 — expected the single end_turn iteration to be terminal but got: " + event);
    }
}
