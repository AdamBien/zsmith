package airhacks.zsmith.claude.boundary;

import airhacks.zsmith.claude.control.Claude;
import airhacks.zsmith.claude.control.Claude.Models;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

/// Which Claude model a request goes to, and what that model accepts.
///
/// The transport picks its model once at startup; this entry point answers the same questions
/// for any catalog model, so the selection and shaping rules can be exercised without a call.
public interface ClaudeModels {

    /// `select-model` — the configured name first, then the requested one, then the default.
    static Models selectModel() {
        return Claude.selectedModel();
    }

    /// `resolve-model-name` — the name the model is sent under for the active provider.
    static String resolveModelName(Models model) {
        return Claude.modelName(model);
    }

    /// `compose-request` — a request body carrying only the settings the model accepts.
    static JSONObject composeRequest(Models model, String system, JSONArray messages, float temperature) {
        return Claude.claudeMessage(model, messages, temperature, system);
    }
}
