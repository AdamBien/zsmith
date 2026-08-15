package airhacks.zsmith.claude.control;

import java.util.Set;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

/// Places `cache_control` breakpoints on outgoing Anthropic Messages requests.
///
/// Prompt caching is a prefix match over `tools` → `system` → `messages`, so two
/// breakpoints cover the whole request: one on the system prompt (which, sitting after
/// the tools in the rendered prompt, caches the tool definitions and the system prompt
/// together) and one on the newest message, which lets every agent-loop iteration
/// re-read the entire prior conversation at cache-read price instead of re-sending it
/// at full price. Cache hits surface in the existing `usage` telemetry as
/// `cache_read_input_tokens`.
///
/// The markers go on a per-request copy, never into the stored conversation: the API
/// allows at most four breakpoints per request, so a marker persisted in
/// [airhacks.zsmith.memory.entity.Memory] would accumulate one per turn and eventually
/// reject the request. String content is normalized to block form on every message so
/// a turn serializes the same way whether or not it carries the marker this request.
///
/// Manual block placement rather than the top-level `cache_control` convenience is
/// deliberate: Bedrock supports explicit breakpoints but not automatic caching, so
/// this one form serves both transports.
///
/// - [Prompt caching](https://docs.claude.com/en/docs/build-with-claude/prompt-caching)
public interface CacheControl {

    String ENABLED_KEY = "claude.cache";
    String TTL_KEY = "claude.cache.ttl";

    /// Block types the API accepts a `cache_control` marker on.
    Set<String> CACHEABLE_TYPES = Set.of("text", "image", "tool_use", "tool_result", "document");

    static boolean enabled() {
        return ZCfg.bool(ENABLED_KEY, true);
    }

    /// The system prompt as a single cached text block, or unchanged when blank or
    /// caching is off.
    static Object system(String system) {
        if (!enabled() || system == null || system.isBlank()) {
            return system;
        }
        return new JSONArray()
                .put(textBlock(system).put("cache_control", marker()));
    }

    /// A copy of the conversation with the marker on the last content block of the
    /// newest message. The input and its nested structures stay untouched.
    static JSONArray messages(JSONArray messages) {
        if (!enabled() || messages.isEmpty()) {
            return messages;
        }
        var decorated = new JSONArray();
        var newest = messages.length() - 1;
        for (var i = 0; i < messages.length(); i++) {
            var message = messages.getJSONObject(i);
            var content = message.opt("content");
            if (content == null) {
                decorated.put(message);
                continue;
            }
            var blocks = normalized(content);
            if (i == newest) {
                blocks = marked(blocks);
            }
            decorated.put(shallowCopy(message).put("content", blocks));
        }
        return decorated;
    }

    private static Object normalized(Object content) {
        return switch (content) {
            case JSONArray blocks -> blocks;
            case String text -> new JSONArray().put(textBlock(text));
            default -> content;
        };
    }

    private static Object marked(Object content) {
        if (!(content instanceof JSONArray blocks) || blocks.isEmpty()) {
            return content;
        }
        var lastIndex = blocks.length() - 1;
        if (!(blocks.opt(lastIndex) instanceof JSONObject last)
                || !CACHEABLE_TYPES.contains(last.optString("type"))) {
            return blocks;
        }
        var copy = new JSONArray();
        for (var i = 0; i < lastIndex; i++) {
            copy.put(blocks.get(i));
        }
        return copy.put(shallowCopy(last).put("cache_control", marker()));
    }

    private static JSONObject textBlock(String text) {
        return new JSONObject()
                .put("type", "text")
                .put("text", text);
    }

    private static JSONObject shallowCopy(JSONObject original) {
        return new JSONObject(original, JSONObject.getNames(original));
    }

    /// Default TTL is five minutes; `claude.cache.ttl=1h` keeps entries alive across
    /// longer pauses at double the cache-write price.
    private static JSONObject marker() {
        var marker = new JSONObject().put("type", "ephemeral");
        var ttl = ZCfg.string(TTL_KEY, null);
        if (ttl != null && !ttl.isBlank()) {
            marker.put("ttl", ttl);
        }
        return marker;
    }
}
