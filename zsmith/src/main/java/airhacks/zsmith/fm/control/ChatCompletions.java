package airhacks.zsmith.fm.control;

import static airhacks.zsmith.Concern.Kind.EXTERNAL_SYSTEM;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import airhacks.zsmith.Concern;
import airhacks.zsmith.configuration.control.HttpTimeouts;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.llm.entity.ToolChoice;
import airhacks.zsmith.openai.control.OpenAI;

/// One turn on the wire. The server speaks the Chat Completions format `openai` already
/// translates, so only what differs for the on-device model is decided here.
@Concern(EXTERNAL_SYSTEM)
public interface ChatCompletions {

    String MODEL_KEY = "fm.model";
    String MAX_TOKENS_KEY = "fm.max.tokens";
    String DEFAULT_MODEL = "system";
    int DEFAULT_MAX_TOKENS = 4096;

    static String model() {
        return ZCfg.string(MODEL_KEY, DEFAULT_MODEL);
    }

    static int maxTokens() {
        return ZCfg.integer(MAX_TOKENS_KEY, DEFAULT_MAX_TOKENS);
    }

    /// The tool choice is always left to the model: the on-device model refuses a forced
    /// tool call outright. Streaming is switched off because the server streams by default.
    static JSONObject request(String system, JSONArray messages, JSONArray tools, float temperature) {
        return OpenAI.translateRequest(system, messages, tools, temperature, model(), maxTokens(), ToolChoice.auto)
                .put("stream", false);
    }

    static HttpResponse<String> send(URI endpoint, String payload) {
        var request = HttpRequest.newBuilder(endpoint)
                .timeout(HttpTimeouts.requestTimeout())
                .POST(BodyPublishers.ofString(payload))
                .header("content-type", "application/json")
                .build();
        try {
            return HttpTimeouts.client().send(request, BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("cannot communicate with the fm server: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the fm server", e);
        }
    }

    /// The normalized reply every provider answers, with the tokens the call used.
    static JSONObject answer(JSONObject completion) {
        var usage = completion.optJSONObject("usage", new JSONObject());
        return OpenAI.translateResponse(completion)
                .put("usage", new JSONObject()
                        .put("input_tokens", usage.optInt("prompt_tokens"))
                        .put("output_tokens", usage.optInt("completion_tokens")));
    }

    static String errorMessage(String body) {
        return OpenAI.extractErrorMessage(body);
    }
}
