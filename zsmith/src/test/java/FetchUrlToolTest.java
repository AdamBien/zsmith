import java.util.Objects;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.tools.boundary.Tools;
import airhacks.zsmith.tools.control.FetchUrlTool;

/// Traces tools spec R2.1, R2.3, R12.3 — see src/main/java/airhacks/zsmith/tools/package-info.java
/// (R12.1 and R12.2 need a served body and are not yet traced.)

void main() {
    var tool = FetchUrlTool.create();

    // R2.1 — The BC shall publish each handler's name, description and input schema.
    assert "fetch_url".equals(tool.toolName()) : "R2.1 — expected 'fetch_url' but got: " + tool.toolName();

    // description is non-empty
    Objects.requireNonNull(tool.description(), "description should not be null");
    assert !tool.description().isBlank() : "description should be non-empty";

    // input schema contains url and required
    var schema = tool.inputSchema().toString();
    assert schema.contains("\"url\"") : "inputSchema should contain '\"url\"'";
    assert schema.contains("\"required\"") : "inputSchema should contain '\"required\"'";

    // missing url returns error
    var missingResult = tool.execute(new JSONObject());
    assert "Error: Missing required parameter: url".equals(missingResult) : "expected error for missing url but got: " + missingResult;

    // R12.3 — If the URL is unreachable, then the BC shall report the failure rather than fail.
    var noScheme = tool.execute(new JSONObject().put("url", "missing-scheme"));
    assert "Error: Invalid URL".equals(noScheme) : "R12.3 — expected 'Error: Invalid URL' but got: " + noScheme;

    // R2.3 — The BC shall report whether a handler may run concurrently with others.
    assert tool.parallel() : "R2.3 — fetch_url should be parallel-safe";

    // can be registered via withTool() and via Tools enum
    var direct = new Agent().withSystemPrompt("hi").withTool(FetchUrlTool.create());
    assert direct.tools().containsKey("fetch_url") : "agent should contain 'fetch_url' tool";

    var enumAgent = new Agent().withSystemPrompt("hi").withTools(Tools.FETCH_URL);
    assert enumAgent.tools().containsKey("fetch_url") : "agent should contain 'fetch_url' via enum";
}
