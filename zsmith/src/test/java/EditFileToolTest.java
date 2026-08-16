import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;
import airhacks.zsmith.tools.boundary.Tool;
import airhacks.zsmith.tools.control.EditFileTool;

/// Traces tools spec R17.1 - R17.6 — see src/main/java/airhacks/zsmith/tools/package-info.java

void main() throws IOException {
    var tempDir = Files.createTempDirectory("zunit-editfile");
    try {
        var tool = EditFileTool.create(new SandboxedFileSystem(tempDir));

        aUniqueOccurrenceIsReplaced(tool, tempDir);
        replaceAllReplacesEveryOccurrenceAndReportsTheCount(tool, tempDir);
        anAbsentTargetIsReported(tool, tempDir);
        anAmbiguousTargetIsRejectedWithItsCount(tool, tempDir);
        anEmptyTargetIsRejected(tool, tempDir);
        anIdenticalReplacementIsRejected(tool, tempDir);
        anAbsentFileIsReported(tool);
    } finally {
        deleteRecursively(tempDir);
    }
}

// R17.1 — When a target text, its replacement and a sandboxed path are supplied and the target
// occurs exactly once in the file, the BC shall replace that occurrence and leave the rest of
// the file unchanged.
void aUniqueOccurrenceIsReplaced(Tool tool, Path root) throws IOException {
    Files.writeString(root.resolve("unique.txt"), "alpha\nbravo\ncharlie\n");
    var result = tool.execute(new JSONObject()
            .put("path", "unique.txt")
            .put("old_string", "bravo")
            .put("new_string", "delta"));
    if (result.startsWith("Error"))
        throw new AssertionError("R17.1 — expected the edit to succeed but got: " + result);
    var content = Files.readString(root.resolve("unique.txt"));
    if (!"alpha\ndelta\ncharlie\n".equals(content))
        throw new AssertionError("R17.1 — expected only the target replaced but got: " + content);
}

// R17.2 — Where replacing every occurrence is requested, the BC shall replace all occurrences
// and report their count.
void replaceAllReplacesEveryOccurrenceAndReportsTheCount(Tool tool, Path root) throws IOException {
    Files.writeString(root.resolve("all.txt"), "tick tock tick");
    var result = tool.execute(new JSONObject()
            .put("path", "all.txt")
            .put("old_string", "tick")
            .put("new_string", "tack")
            .put("replace_all", "true"));
    var content = Files.readString(root.resolve("all.txt"));
    if (!"tack tock tack".equals(content))
        throw new AssertionError("R17.2 — expected every occurrence replaced but got: " + content);
    if (!result.contains("2"))
        throw new AssertionError("R17.2 — expected the occurrence count reported but got: " + result);
}

// R17.3 — If the target text is absent from the file, then the BC shall reject the edit and
// report that the target was not found.
void anAbsentTargetIsReported(Tool tool, Path root) throws IOException {
    Files.writeString(root.resolve("absent.txt"), "alpha");
    var result = tool.execute(new JSONObject()
            .put("path", "absent.txt")
            .put("old_string", "omega")
            .put("new_string", "delta"));
    if (!result.startsWith("Error") || !result.contains("not found"))
        throw new AssertionError("R17.3 — expected a not-found rejection but got: " + result);
    if (!"alpha".equals(Files.readString(root.resolve("absent.txt"))))
        throw new AssertionError("R17.3 — expected the file untouched after the rejection");
}

// R17.4 — If the target text occurs more than once and replacing every occurrence is not
// requested, then the BC shall reject the edit and report the occurrence count.
void anAmbiguousTargetIsRejectedWithItsCount(Tool tool, Path root) throws IOException {
    Files.writeString(root.resolve("ambiguous.txt"), "same\nsame\n");
    var result = tool.execute(new JSONObject()
            .put("path", "ambiguous.txt")
            .put("old_string", "same")
            .put("new_string", "other"));
    if (!result.startsWith("Error") || !result.contains("2"))
        throw new AssertionError("R17.4 — expected a rejection reporting 2 occurrences but got: " + result);
    if (!"same\nsame\n".equals(Files.readString(root.resolve("ambiguous.txt"))))
        throw new AssertionError("R17.4 — expected the file untouched after the rejection");
}

// R17.5 — If the target text is empty or equals its replacement, then the BC shall reject
// the edit.
void anEmptyTargetIsRejected(Tool tool, Path root) throws IOException {
    Files.writeString(root.resolve("empty.txt"), "alpha");
    var result = tool.execute(new JSONObject()
            .put("path", "empty.txt")
            .put("old_string", "")
            .put("new_string", "delta"));
    if (!result.startsWith("Error"))
        throw new AssertionError("R17.5 — expected an empty target rejected but got: " + result);
}

// R17.5 — If the target text is empty or equals its replacement, then the BC shall reject
// the edit.
void anIdenticalReplacementIsRejected(Tool tool, Path root) throws IOException {
    Files.writeString(root.resolve("identical.txt"), "alpha");
    var result = tool.execute(new JSONObject()
            .put("path", "identical.txt")
            .put("old_string", "alpha")
            .put("new_string", "alpha"));
    if (!result.startsWith("Error"))
        throw new AssertionError("R17.5 — expected an identical replacement rejected but got: " + result);
}

// R17.6 — If the requested file is absent, then the BC shall report that it was not found.
void anAbsentFileIsReported(Tool tool) {
    var result = tool.execute(new JSONObject()
            .put("path", "missing.txt")
            .put("old_string", "alpha")
            .put("new_string", "delta"));
    if (!result.contains("File not found"))
        throw new AssertionError("R17.6 — expected a not-found report but got: " + result);
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
