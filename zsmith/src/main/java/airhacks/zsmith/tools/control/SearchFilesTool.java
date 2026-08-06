package airhacks.zsmith.tools.control;

import java.nio.file.Path;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;

public interface SearchFilesTool {

    enum Field { pattern, ending }

    static ToolHandler of(String sandboxPath) {
        return create(new SandboxedFileSystem(Path.of(sandboxPath)));
    }

    static ToolHandler create(SandboxedFileSystem fs) {
        return ToolHandler.of(
                "search_files",
                "Searches file contents within the sandbox directory for a regular expression. "
                        + "Returns matching lines as <relative-path>:<line-number>: <line>, like grep -n. "
                        + "Pass ending to restrict the search to files with that name suffix.",
                ToolHandler.schema(
                        ToolHandler.Prop.string(Field.pattern, "Regular expression to search for"),
                        ToolHandler.Prop.string(Field.ending, "File name suffix filter, e.g. \".java\"; omit to search all files").optional()),
                input -> run(input, fs));
    }

    private static String run(JSONObject input, SandboxedFileSystem fs) {
        if (!input.has(Field.pattern.name())) {
            return "Error: Missing required parameter: pattern";
        }
        var ending = input.has(Field.ending.name()) ? input.getString(Field.ending.name()) : "";
        return fs.searchFiles(input.getString(Field.pattern.name()), ending);
    }
}
