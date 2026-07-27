import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;
import airhacks.zsmith.tools.control.ListFilesEndingTool;

void main() throws IOException {
    var tempDir = Files.createTempDirectory("zunit-listfilesending");
    try {
        Files.writeString(tempDir.resolve("A.java"), "class A {}");
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/B.java"), "class B {}");
        Files.writeString(tempDir.resolve("readme.md"), "# readme");

        var tool = ListFilesEndingTool.create(new SandboxedFileSystem(tempDir));

        assert "list_files_ending".equals(tool.toolName()) : "expected 'list_files_ending' but got: " + tool.toolName();
        var schema = tool.inputSchema().toString();
        assert schema.contains("\"ending\"") : "inputSchema should contain '\"ending\"'";
        assert schema.contains("\"required\"") : "inputSchema should contain '\"required\"'";

        var javaFiles = tool.execute(new JSONObject().put("ending", ".java"));
        assert "A.java\nsub/B.java".equals(javaFiles) : "expected sorted Java files but got: " + javaFiles;
        assert !javaFiles.contains("readme.md") : "listing should not contain readme.md: " + javaFiles;

        var noMatch = tool.execute(new JSONObject().put("ending", ".kt"));
        assert "No files ending with .kt found".equals(noMatch) : "expected no-match message but got: " + noMatch;

        var missingEnding = tool.execute(new JSONObject());
        assert "Error: Missing required parameter: ending".equals(missingEnding)
                : "expected missing-parameter error but got: " + missingEnding;
    } finally {
        deleteRecursively(tempDir);
    }
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
