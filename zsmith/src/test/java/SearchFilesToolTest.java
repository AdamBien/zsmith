import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;
import airhacks.zsmith.tools.control.SearchFilesTool;

void main() throws IOException {
    var tempDir = Files.createTempDirectory("zunit-searchfiles");
    try {
        Files.writeString(tempDir.resolve("A.java"), """
        class A {
            void run() {
                System.out.println("hello");
            }
        }
        """);
        Files.writeString(tempDir.resolve("notes.md"), "System.out is discouraged");

        var tool = SearchFilesTool.create(new SandboxedFileSystem(tempDir));

        assert "search_files".equals(tool.toolName()) : "expected 'search_files' but got: " + tool.toolName();

        var javaOnly = tool.execute(new JSONObject().put("pattern", "System\\.out").put("ending", ".java"));
        assert "A.java:3: System.out.println(\"hello\");".equals(javaOnly)
                : "expected single grep-style match but got: " + javaOnly;

        var allFiles = tool.execute(new JSONObject().put("pattern", "System\\.out"));
        assert allFiles.contains("A.java:3:") && allFiles.contains("notes.md:1:")
                : "expected matches in both files but got: " + allFiles;

        var invalidPattern = tool.execute(new JSONObject().put("pattern", "["));
        assert invalidPattern.startsWith("Error: invalid pattern")
                : "expected invalid-pattern error but got: " + invalidPattern;

        var noMatch = tool.execute(new JSONObject().put("pattern", "doesNotExist"));
        assert "No matches found".equals(noMatch) : "expected no-match message but got: " + noMatch;
    } finally {
        deleteRecursively(tempDir);
    }
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
