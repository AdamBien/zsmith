package airhacks.zsmith.tools.control;

import airhacks.zsmith.tools.boundary.Tool;
import java.nio.file.Path;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;

public interface WriteFileTool {

    enum Field { path, content, append }

    static Tool of(String sandboxPath) {
        return create(new SandboxedFileSystem(Path.of(sandboxPath)));
    }

    static Tool create(SandboxedFileSystem fs) {
        return Tool.of(
                "write_file",
                "Writes content to a file inside the agent's sandbox directory. "
                        + "Path must be relative to the sandbox root; absolute paths and \"..\" segments are rejected. "
                        + "Overwrites the file by default; pass append=\"true\" to append. "
                        + "Creates missing parent directories. "
                        + "Use write_any_file for paths outside the sandbox.",
                Tool.schema(
                        Tool.Prop.string(Field.path, "Relative path to the file to write (sandboxed)"),
                        Tool.Prop.string(Field.content, "Content to write to the file"),
                        Tool.Prop.stringEnum(Field.append, "Append to existing file instead of overwriting", "true", "false").optional()),
                input -> run(input, fs));
    }

    private static String run(JSONObject input, SandboxedFileSystem fs) {
        if (!input.has(Field.path.name())) {
            return "Error: Missing required parameter: path";
        }
        if (!input.has(Field.content.name())) {
            return "Error: Missing required parameter: content";
        }
        var path = input.getString(Field.path.name());
        var append = input.has(Field.append.name())
                && Boolean.parseBoolean(input.getString(Field.append.name()));
        try {
            fs.writeFile(path, input.getString(Field.content.name()), append);
            return (append ? "Appended to file: " : "File written successfully: ") + path;
        } catch (IllegalArgumentException e) {
            return "Error: Invalid path: " + e.getMessage();
        } catch (RuntimeException e) {
            return e.getMessage() != null ? e.getMessage() : "Error: Could not write file";
        }
    }
}
