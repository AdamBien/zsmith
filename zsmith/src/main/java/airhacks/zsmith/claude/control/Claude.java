package airhacks.zsmith.claude.control;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.EnumSet;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;

import airhacks.zcfg.ZCfg;
import airhacks.zsmith.logging.control.Log;



public interface Claude {

    int MAX_TOKENS = 4000;
    String ANTHROPIC_VERSION = ZCfg.string("anthropic.version");
    String ANTHROPIC_API_KEY = ZCfg.string("anthropic.api.key");

    enum Models {
        CLAUDE_45_OPUS("claude-opus-4-5-20251101"),
        CLAUDE_45_SONNET("claude-sonnet-4-5-20250929"),
        CLAUDE_41_OPUS("claude-opus-4-1-20250805"),
        CLAUDE_40_OPUS("claude-opus-4-20250514");

        private String modelName;

        Models(String modelName) {
            this.modelName = modelName;
        }

        public String modelName() {
            return this.modelName;
        }

        boolean matches(String partialName) {
            return this.modelName
                    .toLowerCase()
                    .contains(partialName.toLowerCase());
        }

        public static Models fromSystemProperty() {
            var modelInput = System.getProperty("model");
            return fromPartialMatch(modelInput)
            .orElse(Models.CLAUDE_45_OPUS);
        }

        public static Optional<Models> fromPartialMatch(String partialName) {
            if(partialName == null)
                return Optional.empty();
            return EnumSet.allOf(Models.class)
                    .stream()
                    .filter(model -> model.matches(partialName))
                    .findAny();
         
        }
    }

    HttpClient client = HttpClient.newHttpClient();
    URI uri = URI.create("https://api.anthropic.com/v1/messages");
    Models currentModel = Models.fromSystemProperty();

    static JSONObject invoke(String system, JSONArray messages, JSONArray tools, float temperature) {
        var payloadJSON = claudeMessage(messages, temperature, system);
        payloadJSON.put("model", currentModel.modelName());
        if (tools != null && !tools.isEmpty()) {
            payloadJSON.put("tools", tools);
        }
        var payload = payloadJSON.toString();
        Log.debug(payload);
        var answer = invoke(payload);
        return new JSONObject(answer);
    }

    public static JSONObject invoke(String system, String user, float temperature) {
        var enclosedPrompt = messagePrompt(user);
        Log.debug(enclosedPrompt.toString());
        var payloadJSON = Claude.claudeMessage(enclosedPrompt, temperature, system);
        payloadJSON.put("model", currentModel.modelName());
        var payload = payloadJSON.toString();
        Log.debug(payload);
        var answer = invoke(payload);
        return new JSONObject(answer);
    }

    static JSONObject claudeMessage(JSONArray messages, float temperature, String system) {
        return new JSONObject()
                .put("max_tokens", MAX_TOKENS)
                .put("messages", messages)
                .put("temperature", temperature)
                .put("system", system);

    }

    static JSONArray messagePrompt(String user) {
        return new JSONArray()
                .put(message("user", user));

    }

    static JSONObject message(String role, String content) {
        return new JSONObject()
                .put("role", role)
                .put("content", content);
    }

    /*
     * curl https://api.anthropic.com/v1/messages --header "x-api-key: YOUR_API_KEY"
     * ...
     */
    static String invoke(String message) {
        Log.user("using claude model: %s".formatted(currentModel.modelName()));
        var request = HttpRequest.newBuilder(uri)
                .POST(BodyPublishers.ofString(message))
                .header("x-api-key", ANTHROPIC_API_KEY)
                .header("content-type", "application/json")
                .header("anthropic-version", ANTHROPIC_VERSION)
                .build();
        try {
            var response = client.send(request, BodyHandlers.ofString());
            var body = response.body();
            if (response.statusCode() == 529) {
                Log.error("claude is overloaded, please try again later " + body);
            }
            return body;
        } catch (IOException | InterruptedException e) {
            Log.ERROR.out(e.getMessage());
            throw new IllegalStateException("cannot communicate with claude", e);
        }
    }

}
