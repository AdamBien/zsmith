package airhacks.zsmith.chunking.control;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.llm.control.LLM;
import airhacks.zsmith.llm.entity.LLMResponse;
import airhacks.zsmith.llm.entity.ToolChoice;

/// The one turn a chunk costs, in the shape of [LLM#invoke], so the conversation a chunk is
/// sent in stays visible where the turn is made.
@FunctionalInterface
public interface Model {

    /// Whichever provider `llm` is configured for.
    Model CONFIGURED = LLM::invoke;

    /// The stop reason a reply carries when the output limit cut it off.
    String CUT_OFF = "max_tokens";

    LLMResponse invoke(String system, JSONArray messages, JSONArray tools, float temperature, ToolChoice toolChoice);

    /// A fresh conversation holding the chunk as its only message, the instruction as the
    /// system prompt and no tools.
    default LLMResponse reply(String instruction, String chunk, float temperature) {
        var messages = new JSONArray().put(new JSONObject().put("role", "user").put("content", chunk));
        return invoke(instruction, messages, new JSONArray(), temperature, ToolChoice.auto);
    }
}
