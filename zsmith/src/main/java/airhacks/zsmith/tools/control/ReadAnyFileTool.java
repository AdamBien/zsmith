package airhacks.zsmith.tools.control;

import airhacks.zsmith.tools.boundary.Tool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import airhacks.zsmith.json.JSONObject;

public interface ReadAnyFileTool {

    enum Field { path }

    static Tool create() {
        return Tool.of(
                "read_any_file",
                "Reads a file from any location on the filesystem",
                Tool.schema(Tool.Prop.string(Field.path, "Absolute path to the file to read")),
                ReadAnyFileTool::run);
    }

    private static String run(JSONObject input) {
        if (!input.has(Field.path.name())) {
            return "Error: Missing required parameter: path";
        }
        var path = Path.of(input.getString(Field.path.name()));
        if (!Files.exists(path)) {
            return "Error: File not found: " + path;
        }
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return "Error: Could not read file: " + e.getMessage();
        }
    }
}
