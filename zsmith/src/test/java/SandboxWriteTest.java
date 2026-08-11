import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;
import airhacks.zsmith.tools.control.ReadAnyFileTool;
import airhacks.zsmith.tools.control.WriteFileTool;

/// Traces tools spec R5.1, R5.2, R5.3, R9.1, R9.3
/// — see src/main/java/airhacks/zsmith/tools/package-info.java

void main() throws IOException {
    var tempDir = Files.createTempDirectory("zunit-sandboxwrite");
    try {
        var tool = WriteFileTool.create(new SandboxedFileSystem(tempDir));

        missingParentDirectoriesAreCreated(tool, tempDir);
        appendingPreservesExistingContent(tool, tempDir);
        writingWithoutAppendReplaces(tool, tempDir);
        anAbsolutePathIsRead(tempDir);
        anAbsentUnconfinedFileIsReported();
    } finally {
        deleteRecursively(tempDir);
    }
}

// R5.1 — When content is stored at a sandboxed path, the BC shall create any missing parent
// directories.
void missingParentDirectoriesAreCreated(airhacks.zsmith.tools.control.ToolHandler tool, Path root) throws IOException {
    var result = tool.execute(new JSONObject().put("path", "deeply/nested/note.txt").put("content", "first"));
    if (result.startsWith("Error"))
        throw new AssertionError("R5.1 — expected the nested write to succeed but got: " + result);
    if (!"first".equals(Files.readString(root.resolve("deeply/nested/note.txt"))))
        throw new AssertionError("R5.1 — expected the content stored below the created directories");
}

// R5.2 — Where appending is requested, the BC shall preserve the existing content and add to it.
void appendingPreservesExistingContent(airhacks.zsmith.tools.control.ToolHandler tool, Path root) throws IOException {
    tool.execute(new JSONObject().put("path", "log.txt").put("content", "one"));
    tool.execute(new JSONObject().put("path", "log.txt").put("content", "-two").put("append", "true"));
    var content = Files.readString(root.resolve("log.txt"));
    if (!"one-two".equals(content))
        throw new AssertionError("R5.2 — expected the append to preserve the original but got: " + content);
}

// R5.3 — Where appending is not requested, the BC shall replace the existing content.
void writingWithoutAppendReplaces(airhacks.zsmith.tools.control.ToolHandler tool, Path root) throws IOException {
    tool.execute(new JSONObject().put("path", "replaced.txt").put("content", "original"));
    tool.execute(new JSONObject().put("path", "replaced.txt").put("content", "fresh"));
    var content = Files.readString(root.resolve("replaced.txt"));
    if (!"fresh".equals(content))
        throw new AssertionError("R5.3 — expected the content replaced but got: " + content);
}

// R9.1 — When an absolute path is supplied, the BC shall return that file's contents.
void anAbsolutePathIsRead(Path root) throws IOException {
    var target = root.resolve("unconfined.txt");
    Files.writeString(target, "reachable by absolute path");
    var result = ReadAnyFileTool.create().execute(new JSONObject().put("path", target.toString()));
    if (!"reachable by absolute path".equals(result))
        throw new AssertionError("R9.1 — expected the file's contents but got: " + result);
}

// R9.3 — If the requested file is absent, then the BC shall report that it was not found.
void anAbsentUnconfinedFileIsReported() {
    var result = ReadAnyFileTool.create()
            .execute(new JSONObject().put("path", "/nonexistent/nowhere.txt"));
    if (!result.contains("File not found"))
        throw new AssertionError("R9.3 — expected a not-found report but got: " + result);
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
