package airhacks.zsmith.agentcore.control;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONObject;

import airhacks.zsmith.agentcore.entity.ResultEnvelope;
import airhacks.zsmith.http.boundary.ChatEngine;
import airhacks.zsmith.logging.control.Log;

public record InvocationsHandler(ChatEngine engine, RuntimeSessions sessions) implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            invoke(exchange);
        }
    }

    void invoke(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                Responses.sendJson(exchange, 405, ResultEnvelope.error("Method not allowed — use POST").toJson());
                return;
            }
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                Responses.sendJson(exchange, 400, ResultEnvelope.error("Request body must not be empty").toJson());
                return;
            }
            var sessionId = this.sessions.resolveOrCreate(exchange.getRequestHeaders());
            exchange.getResponseHeaders().add(RuntimeSessions.RUNTIME_HEADER, sessionId);
            var lock = this.sessions.lockFor(sessionId);
            lock.lock();
            try {
                var reply = this.engine.chat(sessionId, prompt(body));
                Responses.sendJson(exchange, 200, ResultEnvelope.success(reply).toJson());
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            Log.error("invocations handler error: " + e.getMessage(), e);
            Responses.sendJson(exchange, 500, ResultEnvelope.error(e.getMessage()).toJson());
        }
    }

    static String prompt(String body) {
        try {
            var prompt = new JSONObject(body).optString("prompt", "");
            return prompt.isBlank() ? body : prompt;
        } catch (IllegalArgumentException notAnObject) {
            return body;
        }
    }
}
