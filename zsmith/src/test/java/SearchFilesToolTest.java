import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;
import airhacks.zsmith.tools.control.SearchFilesTool;

/// Traces tools spec R7.1, R7.2, R7.3 — see src/main/java/airhacks/zsmith/tools/package-info.java
/// (R7.4 truncation and R7.5 unreadable-file skipping are not yet traced.)

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

        // R7.2 — Where a name suffix is supplied, the BC shall search only the files whose name carries it.
        var javaOnly = tool.execute(new JSONObject().put("pattern", "System\\.out").put("ending", ".java"));
        assert "A.java:3: System.out.println(\"hello\");".equals(javaOnly)
                : "R7.2 — expected single grep-style match but got: " + javaOnly;

        // R7.1 — When a regular expression is supplied, the BC shall return each matching line
        // with its root-relative path and line number.
        var allFiles = tool.execute(new JSONObject().put("pattern", "System\\.out"));
        assert allFiles.contains("A.java:3:") && allFiles.contains("notes.md:1:")
                : "R7.1 — expected matches in both files but got: " + allFiles;

        // R7.3 — If the expression is malformed, then the BC shall report it as invalid rather than fail.
        var invalidPattern = tool.execute(new JSONObject().put("pattern", "["));
        assert invalidPattern.startsWith("Error: invalid pattern")
                : "R7.3 — expected invalid-pattern error but got: " + invalidPattern;

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
