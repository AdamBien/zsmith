package airhacks.zsmith.tools.control;

import java.nio.file.Path;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;

public interface ReadFileTool {

    enum Field { path, offset, limit, numbered }

    static ToolHandler of(String sandboxPath) {
        return create(new SandboxedFileSystem(Path.of(sandboxPath)));
    }

    static ToolHandler create(SandboxedFileSystem fs) {
        return ToolHandler.of(
                "read_file",
                "Reads the contents of a file within the sandbox directory. "
                        + "Pass offset and limit to read only part of a large file; a partial read is "
                        + "headed by the lines it covers and the file's total line count. "
                        + "Pass numbered to prefix every returned line with its line number, so findings "
                        + "can cite a location the way search_files reports one.",
                ToolHandler.schema(
                        ToolHandler.Prop.string(Field.path, "Relative path to the file to read"),
                        ToolHandler.Prop.integer(Field.offset, "First line to read, starting at 1; omit to read from the beginning").optional(),
                        ToolHandler.Prop.integer(Field.limit, "Maximum number of lines to read; omit to read to the end").optional(),
                        ToolHandler.Prop.bool(Field.numbered, "Prefix each line with its line number; defaults to false").optional()),
                input -> run(input, fs));
    }

    private static String run(JSONObject input, SandboxedFileSystem fs) {
        if (!input.has(Field.path.name())) {
            return "Error: Missing required parameter: path";
        }
        var offset = input.optInt(Field.offset.name(), 1);
        var limit = input.optInt(Field.limit.name(), LineRange.TO_END);
        var numbered = input.optBoolean(Field.numbered.name(), false);
        LineRange range;
        try {
            range = new LineRange(offset, limit);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
        try {
            return fs.readFile(input.getString(Field.path.name()), range, numbered);
        } catch (IllegalArgumentException e) {
            return "Error: Invalid path";
        }
    }
}
