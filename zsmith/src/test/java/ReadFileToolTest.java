import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;
import airhacks.zsmith.tools.boundary.Tool;
import airhacks.zsmith.tools.control.ReadFileTool;

/// Traces tools spec R4.1 - R4.7 — see src/main/java/airhacks/zsmith/tools/package-info.java

static final String FIVE_LINES = """
        alpha
        bravo
        charlie
        delta
        echo
        """;

void main() throws IOException {
    var tempDir = Files.createTempDirectory("zunit-readfile");
    try {
        Files.writeString(tempDir.resolve("five.txt"), FIVE_LINES);
        var tool = ReadFileTool.create(new SandboxedFileSystem(tempDir));

        wholeFileIsReturnedUnnumbered(tool);
        numberingIsOptIn(tool);
        rangeReturnsOnlyItsLines(tool);
        slicedReadReportsTotalLineCount(tool);
        rangePastTheEndStopsAtTheLastLine(tool);
        rangeStartingPastTheEndIsRejected(tool);
        absentFileIsReported(tool);
    } finally {
        deleteRecursively(tempDir);
    }
}

// R4.1 — When a sandboxed file is requested without a line range, the BC shall return its
// full contents unnumbered.
void wholeFileIsReturnedUnnumbered(Tool tool) {
    var result = tool.execute(new JSONObject().put("path", "five.txt"));
    if (!FIVE_LINES.equals(result))
        throw new AssertionError("R4.1 — expected the verbatim file but got: " + result);
    if (result.contains("\t"))
        throw new AssertionError("R4.1 — expected no line numbers by default but got: " + result);
}

// R4.2 — Where line numbering is requested, the BC shall prefix each returned line with its
// absolute line number.
void numberingIsOptIn(Tool tool) {
    var result = tool.execute(new JSONObject().put("path", "five.txt").put("numbered", true));
    if (!result.startsWith("     1\talpha"))
        throw new AssertionError("R4.2 — expected the first line numbered 1 but got: " + result);
    if (!result.contains("     5\techo"))
        throw new AssertionError("R4.2 — expected the last line numbered 5 but got: " + result);
}

// R4.3 — When a line range is requested, the BC shall return only the lines it covers.
void rangeReturnsOnlyItsLines(Tool tool) {
    var result = tool.execute(new JSONObject().put("path", "five.txt").put("offset", 2).put("limit", 2));
    if (!result.contains("bravo") || !result.contains("charlie"))
        throw new AssertionError("R4.3 — expected lines 2 and 3 but got: " + result);
    if (result.contains("alpha") || result.contains("delta") || result.contains("echo"))
        throw new AssertionError("R4.3 — expected no line outside the range but got: " + result);
}

// R4.4 — When a line range is returned, the BC shall report the file's total line count.
void slicedReadReportsTotalLineCount(Tool tool) {
    var result = tool.execute(new JSONObject().put("path", "five.txt").put("offset", 2).put("limit", 2));
    if (!result.startsWith("[lines 2-3 of 5]"))
        throw new AssertionError("R4.4 — expected the covered window and total of 5 but got: " + result);
}

// R4.5 — If a line range extends past the last line, then the BC shall return the lines up to
// the last line.
void rangePastTheEndStopsAtTheLastLine(Tool tool) {
    var result = tool.execute(new JSONObject().put("path", "five.txt").put("offset", 4).put("limit", 99));
    if (!result.startsWith("[lines 4-5 of 5]"))
        throw new AssertionError("R4.5 — expected the window clamped to line 5 but got: " + result);
    if (!result.contains("delta") || !result.contains("echo"))
        throw new AssertionError("R4.5 — expected the remaining lines but got: " + result);
}

// R4.6 — If a line range starts past the last line, then the BC shall report that the range
// lies beyond the file.
void rangeStartingPastTheEndIsRejected(Tool tool) {
    var result = tool.execute(new JSONObject().put("path", "five.txt").put("offset", 500));
    if (!result.startsWith("Error: offset 500 is beyond the last line"))
        throw new AssertionError("R4.6 — expected a beyond-the-file report but got: " + result);
    if (!result.contains("5 lines"))
        throw new AssertionError("R4.6 — expected the actual line count in the report but got: " + result);
}

// R4.7 — If the requested file is absent, then the BC shall report that it was not found.
void absentFileIsReported(Tool tool) {
    var result = tool.execute(new JSONObject().put("path", "missing.txt"));
    if (!result.contains("File not found"))
        throw new AssertionError("R4.7 — expected a not-found report but got: " + result);
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
