package airhacks.zsmith.tools.control;

import airhacks.zsmith.tools.boundary.Tool;
import java.nio.file.Path;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;

public interface ListFilesEndingTool {

    enum Field { ending }

    static Tool of(String sandboxPath) {
        return create(new SandboxedFileSystem(Path.of(sandboxPath)));
    }

    static Tool create(SandboxedFileSystem fs) {
        return Tool.of(
                "list_files_ending",
                "Lists all files within the sandbox directory whose names end with the given suffix, one relative path per line",
                Tool.schema(Tool.Prop.string(Field.ending, "File name suffix to match, e.g. \".java\"")),
                input -> run(input, fs));
    }

    private static String run(JSONObject input, SandboxedFileSystem fs) {
        if (!input.has(Field.ending.name())) {
            return "Error: Missing required parameter: ending";
        }
        return fs.listFiles(input.getString(Field.ending.name()));
    }
}
