package airhacks.zsmith.tools.control;

import airhacks.zsmith.tools.boundary.Tool;
import java.nio.file.Path;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;

public interface FindFilesTool {

    enum Field { pattern }

    static Tool of(String sandboxPath) {
        return create(new SandboxedFileSystem(Path.of(sandboxPath)));
    }

    static Tool create(SandboxedFileSystem fs) {
        return Tool.of(
                "find_files",
                "Finds all files within the sandbox directory whose name or relative path matches the given glob pattern, one relative path per line. A plain name pattern matches at any depth, e.g. \"Dockerfile*\" finds Dockerfile and src/main/docker/Dockerfile.jvm; a pattern with slashes is matched against the relative path, e.g. \"src/main/docker/*\"",
                Tool.schema(Tool.Prop.string(Field.pattern, "Glob pattern, e.g. \"Dockerfile*\", \"*.yaml\" or \"src/main/**\"")),
                input -> run(input, fs));
    }

    private static String run(JSONObject input, SandboxedFileSystem fs) {
        if (!input.has(Field.pattern.name())) {
            return "Error: Missing required parameter: pattern";
        }
        return fs.findFiles(input.getString(Field.pattern.name()));
    }
}
