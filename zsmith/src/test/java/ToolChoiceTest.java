import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.lightmetal.control.LightMetal;
import airhacks.zsmith.llm.entity.ToolChoice;
import airhacks.zsmith.openai.control.OpenAI;

void main() {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());

    demandsAToolCallOnlyOnTheOpeningTurn();
    rendersBothWireFormats();
    leavesToollessRequestsAlone();
}

/// Demanding a tool call on every turn would remove the agent loop's only exit.
void demandsAToolCallOnlyOnTheOpeningTurn() {
    assert ToolChoice.required == ToolChoice.forTurn(0) : "the opening turn demands a tool call by default";
    assert ToolChoice.auto == ToolChoice.forTurn(1) : "later turns must stay auto, or the loop never ends";

    // the escape hatch, for endpoints that do not accept tool_choice
    System.setProperty(ToolChoice.REQUIRE_FIRST_KEY, "false");
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    try {
        assert ToolChoice.auto == ToolChoice.forTurn(0) : "the property should switch the demand off";
    } finally {
        System.clearProperty(ToolChoice.REQUIRE_FIRST_KEY);
        ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());
    }
}

void rendersBothWireFormats() {
    var messages = new JSONArray().put(new JSONObject().put("role", "user").put("content", "go"));

    var openai = OpenAI.translateRequest(null, messages, tools(), 0.5f, "some-model", 1024, ToolChoice.required);
    assert "required".equals(openai.optString("tool_choice", null))
            : "OpenAI expects the string 'required', got: " + openai.opt("tool_choice");

    var anthropic = LightMetal.anthropicPayload(null, messages, tools(), 0.5f, ToolChoice.required);
    assert "any".equals(anthropic.getJSONObject("tool_choice").optString("type", null))
            : "Anthropic expects {type: any}, got: " + anthropic.opt("tool_choice");
}

/// Sending tool_choice without tools is rejected by both APIs.
void leavesToollessRequestsAlone() {
    var messages = new JSONArray().put(new JSONObject().put("role", "user").put("content", "go"));

    var noTools = OpenAI.translateRequest(null, messages, new JSONArray(), 0.5f, "some-model", 1024, ToolChoice.required);
    assert !noTools.has("tool_choice") : "no tools means no tool_choice, got: " + noTools;

    var auto = OpenAI.translateRequest(null, messages, tools(), 0.5f, "some-model", 1024, ToolChoice.auto);
    assert !auto.has("tool_choice") : "auto stays off the wire, got: " + auto;
}

JSONArray tools() {
    return new JSONArray().put(new JSONObject()
            .put("name", "user_question")
            .put("description", "asks the user")
            .put("input_schema", new JSONObject().put("type", "object").put("properties", new JSONObject())));
}
