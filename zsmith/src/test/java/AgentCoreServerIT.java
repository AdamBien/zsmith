import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONObject;

import airhacks.zsmith.agentcore.boundary.AgentCoreServer;
import airhacks.zsmith.http.boundary.ChatEngine;

/// Traces agentcore spec R1, R2, R3 — see src/main/java/airhacks/zsmith/agentcore/package-info.java

static final String RUNTIME_HEADER = "X-Amzn-Bedrock-AgentCore-Runtime-Session-Id";

Duration timeout = Duration.ofSeconds(2);
HttpClient client = HttpClient.newHttpClient();
ConcurrentMap<String, Throwable> failures = new ConcurrentHashMap<>();

void main() {
    var tests = List.<Runnable>of(
            this::contractServesInvocationsAndHealthOnBoundPort,
            this::stoppedServerReleasesPort,
            this::promptExtractionRows,
            this::runtimeSessionIdScopesEngineAndEchoes,
            this::fallbackSessionHeaderScopesEngine,
            this::missingSessionIdIsGeneratedAndEchoed,
            this::sameSessionInvocationsAreSerialized,
            this::rejectionRows,
            this::failingEngineAnswersErrorEnvelope,
            this::healthProbeAnswersHealthyWithTime);

    try (var ignored = this.client) {
        tests.parallelStream().forEach(this::run);
    }

    if (!this.failures.isEmpty()) {
        this.failures.values().forEach(Throwable::printStackTrace);
        throw new AssertionError(this.failures.size() + " of " + tests.size() + " tests failed");
    }
    IO.println("AgentCoreServerIT: all " + tests.size() + " tests passed");
}

void run(Runnable test) {
    try {
        test.run();
    } catch (Throwable failure) {
        this.failures.put(test.toString(), failure);
    }
}

// R1.1 — When the contract server is started, the BC shall accept invocation and health requests
// on the configured port on all interfaces.
// R1.2 — Where an ephemeral port is requested, the BC shall bind a free port and report the bound port.
void contractServesInvocationsAndHealthOnBoundPort() {
    var server = AgentCoreServer.start((sessionId, message) -> "pong", 0);
    try {
        assert server.port() > 0 : "R1.2 — expected a reported ephemeral port but got: " + server.port();
        var invocation = post(server, "/invocations", null, "{\"prompt\":\"hi\"}");
        assert invocation.statusCode() == 200 : "R1.1 — expected 200 from /invocations but got " + invocation.statusCode();
        var health = get(server, "/ping");
        assert health.statusCode() == 200 : "R1.1 — expected 200 from /ping but got " + health.statusCode();
    } catch (Exception ex) {
        throw new RuntimeException(ex);
    } finally {
        server.stop();
    }
}

// R1.3 — When the server is stopped, the BC shall release the port and its workers.
void stoppedServerReleasesPort() {
    var server = AgentCoreServer.start((sessionId, message) -> "up", 0);
    var port = server.port();
    server.stop();
    try {
        var response = this.client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ping"))
                .timeout(this.timeout).GET().build(), BodyHandlers.ofString());
        throw new AssertionError("R1.3 — expected connection failure after stop but got " + response.statusCode());
    } catch (AssertionError failure) {
        throw failure;
    } catch (Exception expected) {
        // connection refused proves the port is released
    }
}

// R2.1 — When an invocation payload carries a prompt, the BC shall pass the prompt to the engine
// and answer with a success envelope containing the engine's reply.
// R2.2 — If an invocation payload carries no prompt, then the BC shall pass the raw payload to the engine.
record PromptRow(String id, String body, String expectedMessage) {
}

void promptExtractionRows() {
    var rows = List.of(
            new PromptRow("R2.1", "{\"prompt\":\"hi\"}", "hi"),
            new PromptRow("R2.2", "{\"input\":\"no prompt key\"}", "{\"input\":\"no prompt key\"}"),
            new PromptRow("R2.2", "plain text payload", "plain text payload"));
    var seen = new ConcurrentHashMap<String, String>();
    var server = AgentCoreServer.start((sessionId, message) -> {
        seen.put(sessionId, message);
        return "echo:" + message;
    }, 0);
    try {
        for (var row : rows) {
            var response = post(server, "/invocations", row.id() + "-session", row.body());
            assert response.statusCode() == 200 : row.id() + " — expected 200 but got " + response.statusCode();
            var contentType = response.headers().firstValue("Content-Type").orElse("");
            assert contentType.startsWith("application/json") : row.id() + " — expected JSON content type but got: " + contentType;
            var envelope = new JSONObject(response.body());
            assert "success".equals(envelope.getString("status")) : row.id() + " — expected success status but got: " + envelope;
            assert ("echo:" + row.expectedMessage()).equals(envelope.getString("response"))
                    : row.id() + " — expected engine reply in envelope but got: " + envelope;
            assert row.expectedMessage().equals(seen.get(row.id() + "-session"))
                    : row.id() + " — expected engine to receive '" + row.expectedMessage() + "' but got: " + seen.get(row.id() + "-session");
        }
    } catch (Exception ex) {
        throw new RuntimeException(ex);
    } finally {
        server.stop();
    }
}

// R2.3 — When an invocation carries a runtime session id, the BC shall scope the engine
// conversation to that session and echo the id in the response.
void runtimeSessionIdScopesEngineAndEchoes() {
    var seen = new ConcurrentHashMap<String, String>();
    var server = AgentCoreServer.start((sessionId, message) -> {
        seen.put(sessionId, message);
        return "ok";
    }, 0);
    try {
        var response = post(server, "/invocations", "runtime-session-42", "{\"prompt\":\"question\"}");
        assert response.statusCode() == 200 : "R2.3 — expected 200 but got " + response.statusCode();
        var echoed = response.headers().firstValue(RUNTIME_HEADER).orElse(null);
        assert "runtime-session-42".equals(echoed) : "R2.3 — expected echoed runtime session id but got: " + echoed;
        assert "question".equals(seen.get("runtime-session-42")) : "R2.3 — engine should see the runtime session id";
    } catch (Exception ex) {
        throw new RuntimeException(ex);
    } finally {
        server.stop();
    }
}

// R2.3 — the fallback session header scopes the conversation the same way.
void fallbackSessionHeaderScopesEngine() {
    var seen = new ConcurrentHashMap<String, String>();
    var server = AgentCoreServer.start((sessionId, message) -> {
        seen.put(sessionId, message);
        return "ok";
    }, 0);
    try {
        var request = HttpRequest.newBuilder(uri(server, "/invocations"))
                .timeout(this.timeout)
                .header("X-Session-Id", "fallback-7")
                .POST(BodyPublishers.ofString("{\"prompt\":\"via fallback\"}"))
                .build();
        var response = this.client.send(request, BodyHandlers.ofString());
        assert response.statusCode() == 200 : "R2.3 — expected 200 but got " + response.statusCode();
        var echoed = response.headers().firstValue(RUNTIME_HEADER).orElse(null);
        assert "fallback-7".equals(echoed) : "R2.3 — expected fallback id echoed on runtime header but got: " + echoed;
        assert "via fallback".equals(seen.get("fallback-7")) : "R2.3 — engine should see the fallback session id";
    } catch (Exception ex) {
        throw new RuntimeException(ex);
    } finally {
        server.stop();
    }
}

// R2.4 — If an invocation carries no session id, then the BC shall generate one and echo it in the response.
void missingSessionIdIsGeneratedAndEchoed() {
    var seen = new ConcurrentHashMap<String, String>();
    var server = AgentCoreServer.start((sessionId, message) -> {
        seen.put(sessionId, message);
        return "ok";
    }, 0);
    try {
        var response = post(server, "/invocations", null, "{\"prompt\":\"anonymous\"}");
        assert response.statusCode() == 200 : "R2.4 — expected 200 but got " + response.statusCode();
        var generated = response.headers().firstValue(RUNTIME_HEADER).orElse(null);
        assert generated != null && !generated.isBlank() : "R2.4 — expected a generated session id header";
        assert "anonymous".equals(seen.get(generated)) : "R2.4 — engine should see the generated session id";
    } catch (Exception ex) {
        throw new RuntimeException(ex);
    } finally {
        server.stop();
    }
}

// R2.5 — While an invocation of a session is in flight, the BC shall defer further invocations
// of the same session until it completes.
void sameSessionInvocationsAreSerialized() {
    var inFlight = new AtomicInteger();
    var maxInFlight = new AtomicInteger();
    var server = AgentCoreServer.start((sessionId, message) -> {
        var concurrent = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(concurrent, Math::max);
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        inFlight.decrementAndGet();
        return "done";
    }, 0);
    try {
        var ready = new CountDownLatch(2);
        Runnable invocation = () -> {
            try {
                ready.countDown();
                var response = post(server, "/invocations", "shared-session", "{\"prompt\":\"turn\"}");
                assert response.statusCode() == 200 : "R2.5 — expected 200 but got " + response.statusCode();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        };
        var first = Thread.ofVirtual().start(invocation);
        var second = Thread.ofVirtual().start(invocation);
        first.join();
        second.join();
        assert maxInFlight.get() == 1 : "R2.5 — expected serialized engine calls per session but saw " + maxInFlight.get() + " in flight";
    } catch (InterruptedException ex) {
        throw new RuntimeException(ex);
    } finally {
        server.stop();
    }
}

// R2.6 — If an invocation payload is empty, then the BC shall reject the invocation.
// R2.7 — If an invocation arrives via an unsupported operation, then the BC shall refuse it.
// R3.2 — If a health probe arrives via an unsupported operation, then the BC shall refuse it.
record RejectionRow(String id, String method, String path, String body, int expectedStatus) {
}

void rejectionRows() {
    var rows = List.of(
            new RejectionRow("R2.6", "POST", "/invocations", "", 400),
            new RejectionRow("R2.7", "GET", "/invocations", null, 405),
            new RejectionRow("R3.2", "POST", "/ping", "probe", 405));
    var invocations = new AtomicInteger();
    var server = AgentCoreServer.start((sessionId, message) -> {
        invocations.incrementAndGet();
        return "should not be called";
    }, 0);
    try {
        for (var row : rows) {
            var builder = HttpRequest.newBuilder(uri(server, row.path())).timeout(this.timeout);
            var request = row.body() == null
                    ? builder.method(row.method(), HttpRequest.BodyPublishers.noBody()).build()
                    : builder.method(row.method(), BodyPublishers.ofString(row.body())).build();
            var response = this.client.send(request, BodyHandlers.ofString());
            assert response.statusCode() == row.expectedStatus()
                    : row.id() + " — expected " + row.expectedStatus() + " but got " + response.statusCode();
            var envelope = new JSONObject(response.body());
            assert "error".equals(envelope.getString("status")) : row.id() + " — expected error status but got: " + envelope;
        }
        assert invocations.get() == 0 : "engine should not have been invoked for rejected requests";
    } catch (Exception ex) {
        throw new RuntimeException(ex);
    } finally {
        server.stop();
    }
}

// R2.8 — If the engine fails, then the BC shall answer with an error envelope instead of
// propagating the failure.
void failingEngineAnswersErrorEnvelope() {
    var server = AgentCoreServer.start((sessionId, message) -> {
        throw new IllegalStateException("engine exploded R2.8");
    }, 0);
    try {
        var response = post(server, "/invocations", null, "{\"prompt\":\"boom\"}");
        assert response.statusCode() == 500 : "R2.8 — expected 500 but got " + response.statusCode();
        var envelope = new JSONObject(response.body());
        assert "error".equals(envelope.getString("status")) : "R2.8 — expected error status but got: " + envelope;
        assert envelope.getString("response").contains("engine exploded R2.8")
                : "R2.8 — expected the failure summary in the envelope but got: " + envelope;
    } catch (Exception ex) {
        throw new RuntimeException(ex);
    } finally {
        server.stop();
    }
}

// R3.1 — When health is probed, the BC shall answer healthy with the seconds-precision answer time.
void healthProbeAnswersHealthyWithTime() {
    var server = AgentCoreServer.start((sessionId, message) -> "unused", 0);
    try {
        var response = get(server, "/ping");
        assert response.statusCode() == 200 : "R3.1 — expected 200 but got " + response.statusCode();
        var payload = new JSONObject(response.body());
        assert "Healthy".equals(payload.getString("status")) : "R3.1 — expected Healthy status but got: " + payload;
        assert payload.getLong("time_of_last_update") > 0 : "R3.1 — expected an epoch-seconds timestamp but got: " + payload;
    } catch (Exception ex) {
        throw new RuntimeException(ex);
    } finally {
        server.stop();
    }
}

HttpResponse<String> post(AgentCoreServer server, String path, String sessionId, String body) throws Exception {
    var builder = HttpRequest.newBuilder(uri(server, path))
            .timeout(this.timeout)
            .POST(BodyPublishers.ofString(body));
    if (sessionId != null) {
        builder.header(RUNTIME_HEADER, sessionId);
    }
    return this.client.send(builder.build(), BodyHandlers.ofString());
}

HttpResponse<String> get(AgentCoreServer server, String path) throws Exception {
    var request = HttpRequest.newBuilder(uri(server, path)).timeout(this.timeout).GET().build();
    return this.client.send(request, BodyHandlers.ofString());
}

URI uri(AgentCoreServer server, String path) {
    return URI.create("http://localhost:" + server.port() + path);
}
