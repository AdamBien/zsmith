package airhacks.zsmith.llm.entity;

import java.util.ArrayList;
import java.util.List;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.entity.ToolUse;

/// One model reply in the normalized content-block shape every provider is mapped to:
/// the blocks as sent, so they can be echoed back into the conversation verbatim, and the
/// stop reason that says whether the model is done or is asking for tools.
public record LLMResponse(JSONArray content, String stopReason) {

    public static LLMResponse fromJSON(JSONObject response) {
        return new LLMResponse(response.getJSONArray("content"), response.optString("stop_reason", "end_turn"));
    }

    /// The prose blocks joined with newlines; empty when the reply consists of tool calls only.
    public String text() {
        var texts = new ArrayList<String>();
        for (int i = 0; i < this.content.length(); i++) {
            var block = this.content.getJSONObject(i);
            if ("text".equals(block.optString("type"))) {
                texts.add(block.getString("text"));
            }
        }
        return String.join("\n", texts);
    }

    public List<ToolUse> toolUses() {
        var toolUses = new ArrayList<ToolUse>();
        for (int i = 0; i < this.content.length(); i++) {
            var block = this.content.getJSONObject(i);
            if (ToolUse.isToolUse(block)) {
                toolUses.add(ToolUse.fromJSON(block));
            }
        }
        return toolUses;
    }

    /// The loop continues only when the model both stopped for tool use and named at least
    /// one tool; either alone is a final answer.
    public boolean requestsTools() {
        return "tool_use".equals(this.stopReason) && !toolUses().isEmpty();
    }
}
