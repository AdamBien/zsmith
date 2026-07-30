package airhacks.zsmith.agentcore.control;

import java.io.IOException;
import java.time.Instant;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.json.JSONObject;

import airhacks.zsmith.agentcore.entity.ResultEnvelope;

public record PingHandler() implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Responses.sendJson(exchange, 405, ResultEnvelope.error("Method not allowed — use GET").toJson());
                return;
            }
            var payload = new JSONObject()
                    .put("status", "Healthy")
                    .put("time_of_last_update", Instant.now().getEpochSecond());
            Responses.sendJson(exchange, 200, payload.toString());
        }
    }
}
