package airhacks.zsmith.memory.entity;

import java.util.ArrayList;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

public record Message(String role, Object content) {

    public JSONObject toJSON() {
        var json = new JSONObject().put("role", this.role);
        if (this.content instanceof String s) {
            json.put("content", s);
        } else if (this.content instanceof JSONArray arr) {
            json.put("content", arr);
        } else {
            json.put("content", this.content);
        }
        return json;
    }

    /// The prose of this message: the string itself, or its text blocks joined with newlines.
    /// Empty for a message that consists of tool calls or tool results only.
    public String text() {
        if (this.content instanceof String plain) {
            return plain;
        }
        if (!(this.content instanceof JSONArray blocks)) {
            return "";
        }
        var texts = new ArrayList<String>();
        for (int i = 0; i < blocks.length(); i++) {
            var block = blocks.optJSONObject(i);
            if (block != null && "text".equals(block.optString("type"))) {
                texts.add(block.optString("text"));
            }
        }
        return String.join("\n", texts);
    }

    public static Message fromJSON(JSONObject json) {
        var role = json.getString("role");
        var content = json.get("content");
        return new Message(role, content);
    }

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

    public static Message withContentBlocks(String role, JSONArray contentBlocks) {
        return new Message(role, contentBlocks);
    }
}
