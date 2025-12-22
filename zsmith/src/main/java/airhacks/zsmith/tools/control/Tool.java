package airhacks.zsmith.tools.control;

import org.json.JSONObject;

public interface Tool {

    String name();

    String description();

    String inputSchema();

    String execute(JSONObject input);

    default JSONObject toToolDefinition() {
        return new JSONObject()
                .put("name", name())
                .put("description", description())
                .put("input_schema", new JSONObject(inputSchema()));
    }
}
