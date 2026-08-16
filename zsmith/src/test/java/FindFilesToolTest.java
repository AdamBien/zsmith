import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;
import airhacks.zsmith.tools.control.FindFilesTool;

/// Traces tools spec R2.1, R6.3, R6.4, R6.5, R6.6 — see src/main/java/airhacks/zsmith/tools/package-info.java

void main() throws IOException {
    var tempDir = Files.createTempDirectory("zunit-findfiles");
    try {
        Files.writeString(tempDir.resolve("Dockerfile"), "FROM alpine");
        Files.writeString(tempDir.resolve("Containerfile"), "FROM alpine");
        Files.createDirectories(tempDir.resolve("src/main/docker"));
        Files.writeString(tempDir.resolve("src/main/docker/Dockerfile.jvm"), "FROM eclipse-temurin");
        Files.writeString(tempDir.resolve("readme.md"), "# readme");

        var tool = FindFilesTool.create(new SandboxedFileSystem(tempDir));

        // R2.1 — The BC shall publish each handler's name, description and input schema.
        assert "find_files".equals(tool.toolName()) : "R2.1 — expected 'find_files' but got: " + tool.toolName();
        var schema = tool.inputSchema().toString();
        assert schema.contains("\"pattern\"") : "R2.1 — inputSchema should contain '\"pattern\"'";
        assert schema.contains("\"required\"") : "R2.1 — inputSchema should contain '\"required\"'";

        // R6.5 — a name glob matches at any depth. R6.3 — stable order.
        var dockerfiles = tool.execute(new JSONObject().put("pattern", "Dockerfile*"));
        assert "Dockerfile\nsrc/main/docker/Dockerfile.jvm".equals(dockerfiles)
                : "R6.5 — expected Dockerfile matches at all depths but got: " + dockerfiles;
        assert !dockerfiles.contains("Containerfile") : "R6.5 — Dockerfile* must not match Containerfile: " + dockerfiles;

        // R6.5 — a glob with separators matches the root-relative path.
        var pathMatches = tool.execute(new JSONObject().put("pattern", "src/main/docker/*"));
        assert "src/main/docker/Dockerfile.jvm".equals(pathMatches)
                : "R6.5 — expected path-glob match but got: " + pathMatches;

        // R6.4 — If no file matches, then the BC shall report that none was found.
        var noMatch = tool.execute(new JSONObject().put("pattern", "*.kt"));
        assert "No files matching *.kt found".equals(noMatch) : "R6.4 — expected no-match message but got: " + noMatch;

        // R6.6 — If the glob pattern is malformed, then the BC shall report it as invalid rather than fail.
        var invalid = tool.execute(new JSONObject().put("pattern", "{unclosed"));
        assert invalid.startsWith("Error: invalid pattern") : "R6.6 — expected invalid-pattern error but got: " + invalid;

        var missingPattern = tool.execute(new JSONObject());
        assert "Error: Missing required parameter: pattern".equals(missingPattern)
                : "expected missing-parameter error but got: " + missingPattern;
    } finally {
        deleteRecursively(tempDir);
    }
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
