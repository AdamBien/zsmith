package airhacks.zsmith.tools.control;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.json.JSONObject;

public class CurrentTimeTool implements Tool {

    @Override
    public String name() {
        return "current_time";
    }

    @Override
    public String description() {
        return "Returns the current date and time";
    }

    @Override
    public JSONObject inputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject());
    }

    @Override
    public String execute(JSONObject input) {
        var now = LocalDateTime.now();
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return now.format(formatter);
    }
}
