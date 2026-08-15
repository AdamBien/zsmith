import airhacks.zsmith.claude.control.CacheControl;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

void main() {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());

    wrapsTheSystemPromptInACachedTextBlock();
    passesBlankSystemThrough();
    marksOnlyTheNewestMessage();
    leavesTheStoredConversationUntouched();
    skipsUncacheableTrailingBlocks();
    respectsTheKillSwitch();
    carriesTheConfiguredTtl();
}

/// The system block's marker caches tools + system together — tools render first,
/// and a breakpoint covers everything before it.
void wrapsTheSystemPromptInACachedTextBlock() {
    var system = CacheControl.system("You are a code reviewer.");
    assert system instanceof JSONArray : "system must become a block array, got: " + system;
    var blocks = (JSONArray) system;
    assert blocks.length() == 1 : "one text block expected, got: " + blocks;
    var block = blocks.getJSONObject(0);
    assert "text".equals(block.getString("type")) : "block type must be text, got: " + block;
    assert "You are a code reviewer.".equals(block.getString("text")) : "prompt text must survive, got: " + block;
    assert "ephemeral".equals(block.getJSONObject("cache_control").getString("type"))
            : "marker must be ephemeral, got: " + block;
}

void passesBlankSystemThrough() {
    assert CacheControl.system(null) == null : "null system stays null";
    assert "".equals(CacheControl.system("")) : "blank system stays blank — nothing to cache";
}

/// Four markers per request is the API limit; one on the newest message is the
/// multi-turn pattern — earlier breakpoints remain valid read points server-side.
void marksOnlyTheNewestMessage() {
    var decorated = CacheControl.messages(conversation());
    assert decorated.length() == 3 : "message count must not change, got: " + decorated.length();

    var opening = decorated.getJSONObject(0).getJSONArray("content");
    assert !opening.getJSONObject(0).has("cache_control") : "older turns carry no marker, got: " + opening;
    assert "review the code".equals(opening.getJSONObject(0).getString("text"))
            : "string content must be normalized to a text block, got: " + opening;

    var newest = decorated.getJSONObject(2).getJSONArray("content");
    assert !newest.getJSONObject(0).has("cache_control") : "only the last block is marked, got: " + newest;
    var lastBlock = newest.getJSONObject(1);
    assert "ephemeral".equals(lastBlock.getJSONObject("cache_control").getString("type"))
            : "newest message's last block must carry the marker, got: " + lastBlock;
}

/// A marker persisted into memory would accumulate one breakpoint per turn and
/// eventually exceed the four-per-request limit.
void leavesTheStoredConversationUntouched() {
    var conversation = conversation();
    var storedToolResults = conversation.getJSONObject(2).getJSONArray("content");

    CacheControl.messages(conversation);

    assert conversation.getJSONObject(0).get("content") instanceof String
            : "stored string content must stay a string";
    assert !storedToolResults.getJSONObject(1).has("cache_control")
            : "the marker must never leak into stored blocks, got: " + storedToolResults;
}

void skipsUncacheableTrailingBlocks() {
    var messages = new JSONArray().put(new JSONObject()
            .put("role", "assistant")
            .put("content", new JSONArray().put(new JSONObject()
                    .put("type", "thinking")
                    .put("thinking", "hmm"))));
    var decorated = CacheControl.messages(messages);
    var block = decorated.getJSONObject(0).getJSONArray("content").getJSONObject(0);
    assert !block.has("cache_control") : "uncacheable block types must stay unmarked, got: " + block;
}

/// For endpoints that reject cache_control, `claude.cache=false` restores the
/// undecorated wire format.
void respectsTheKillSwitch() {
    System.setProperty(CacheControl.ENABLED_KEY, "false");
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    try {
        var conversation = conversation();
        assert CacheControl.messages(conversation) == conversation : "disabled means passthrough";
        assert "prompt".equals(CacheControl.system("prompt")) : "disabled system stays a plain string";
    } finally {
        System.clearProperty(CacheControl.ENABLED_KEY);
        ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    }
}

void carriesTheConfiguredTtl() {
    System.setProperty(CacheControl.TTL_KEY, "1h");
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    try {
        var blocks = (JSONArray) CacheControl.system("prompt");
        var marker = blocks.getJSONObject(0).getJSONObject("cache_control");
        assert "1h".equals(marker.getString("ttl")) : "configured TTL must ride the marker, got: " + marker;
    } finally {
        System.clearProperty(CacheControl.TTL_KEY);
        ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    }
}

/// An agent-loop shape: opening user prompt as a string, assistant turn with a tool
/// call, tool results as the newest message.
JSONArray conversation() {
    return new JSONArray()
            .put(new JSONObject()
                    .put("role", "user")
                    .put("content", "review the code"))
            .put(new JSONObject()
                    .put("role", "assistant")
                    .put("content", new JSONArray()
                            .put(new JSONObject().put("type", "text").put("text", "reading the file"))
                            .put(new JSONObject().put("type", "tool_use").put("id", "tu_1")
                                    .put("name", "read_file").put("input", new JSONObject()))))
            .put(new JSONObject()
                    .put("role", "user")
                    .put("content", new JSONArray()
                            .put(new JSONObject().put("type", "tool_result")
                                    .put("tool_use_id", "tu_1").put("content", "class A {}"))
                            .put(new JSONObject().put("type", "tool_result")
                                    .put("tool_use_id", "tu_2").put("content", "done"))));
}
