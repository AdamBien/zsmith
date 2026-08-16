package airhacks.zsmith.tools.boundary;

import airhacks.zsmith.json.JSONObject;

/// The published contract every tool fulfills — built-in handlers and tools
/// implemented outside the framework alike. The internal ToolHandler extends
/// this contract with factories and schema helpers.
public interface Tool {

    String toolName();

    String description();

    JSONObject inputSchema();

    /// the return value is shown verbatim to the LLM; report recoverable
    /// problems as prose ("Error: division by zero"), throw for real failures —
    /// the agent loop converts the exception into an error tool result
    String execute(JSONObject input);

    default boolean parallel() {
        return false;
    }

    default JSONObject toToolDefinition() {
        return new JSONObject()
                .put("name", toolName())
                .put("description", description())
                .put("input_schema", inputSchema());
    }
}
