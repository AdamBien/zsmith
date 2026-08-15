package airhacks.zsmith.tools.control;

import java.nio.file.Path;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;

public interface EditFileTool {

    enum Field { path, old_string, new_string, replace_all }

    static ToolHandler of(String sandboxPath) {
        return create(new SandboxedFileSystem(Path.of(sandboxPath)));
    }

    static ToolHandler create(SandboxedFileSystem fs) {
        return ToolHandler.of(
                "edit_file",
                "Replaces an exact occurrence of old_string with new_string in a file inside the agent's sandbox directory. "
                        + "Matching is exact and verbatim, including whitespace and line breaks. "
                        + "old_string must occur exactly once; pass replace_all=\"true\" to replace every occurrence instead. "
                        + "Everything outside the match is preserved unchanged; an empty new_string deletes the matched text. "
                        + "Path must be relative to the sandbox root. "
                        + "Use write_file to create a file or replace its whole content.",
                ToolHandler.schema(
                        ToolHandler.Prop.string(Field.path, "Relative path to the file to edit (sandboxed)"),
                        ToolHandler.Prop.string(Field.old_string, "Exact text to replace; must match the file verbatim, including whitespace"),
                        ToolHandler.Prop.string(Field.new_string, "Replacement text; empty deletes the matched text"),
                        ToolHandler.Prop.stringEnum(Field.replace_all, "Replace every occurrence instead of requiring a unique match", "true", "false").optional()),
                input -> run(input, fs));
    }

    private static String run(JSONObject input, SandboxedFileSystem fs) {
        if (!input.has(Field.path.name())) {
            return "Error: Missing required parameter: path";
        }
        if (!input.has(Field.old_string.name())) {
            return "Error: Missing required parameter: old_string";
        }
        if (!input.has(Field.new_string.name())) {
            return "Error: Missing required parameter: new_string";
        }
        var replaceAll = input.has(Field.replace_all.name())
                && Boolean.parseBoolean(input.getString(Field.replace_all.name()));
        try {
            return fs.editFile(
                    input.getString(Field.path.name()),
                    input.getString(Field.old_string.name()),
                    input.getString(Field.new_string.name()),
                    replaceAll);
        } catch (IllegalArgumentException e) {
            return "Error: Invalid path: " + e.getMessage();
        } catch (RuntimeException e) {
            return e.getMessage() != null ? e.getMessage() : "Error: Could not edit file";
        }
    }
}
