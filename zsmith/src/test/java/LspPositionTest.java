import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import airhacks.zsmith.lsp.boundary.Lsp;

/// Traces lsp spec R4.1, R4.2, R4.3, R4.4 — see src/main/java/airhacks/zsmith/lsp/package-info.java
/// The language server is src/test/java/lsp/FakeLanguageServer.java; it logs every method it receives.

void main() throws IOException {
    var root = workspace();
    var log = root.resolve("server.log");
    var lsp = Lsp.of(root, serverCommand(log), Duration.ofSeconds(20));
    try {
        // R4.1 — When a root-relative path, a one-based line and a one-based column are supplied,
        // the BC shall query the server at that position of that file against the file's current on-disk content.
        var definition = lsp.findDefinition("Main.java", 4, 15);
        assert definition.contains("Shape.java:2:10: void draw();")
                : "R4.1 — expected the definition of the symbol at Main.java:4:15 but got: " + definition;
        // the close is a notification: the fake logs it before it reads the next request
        lsp.findSymbols("Circle");
        var entries = Files.readAllLines(log);
        assert entries.indexOf("textDocument/didOpen") < entries.indexOf("textDocument/definition")
                && entries.indexOf("textDocument/definition") < entries.indexOf("textDocument/didClose")
                && entries.indexOf("textDocument/didClose") < entries.indexOf("workspace/symbol")
                : "R4.1 — expected the file content opened at the server before the query and closed after, log: " + entries;

        // R4.2 — If the path is absolute, escapes the workspace root or names an absent file,
        // then the BC shall reject the request.
        var absolute = lsp.findDefinition(root.resolve("Main.java").toString(), 4, 15);
        assert absolute.startsWith("Error: path must be relative to the workspace root")
                : "R4.2 — expected the absolute path rejected but got: " + absolute;
        var escaping = lsp.findDefinition("../outside.java", 1, 1);
        assert escaping.startsWith("Error: path escapes the workspace root")
                : "R4.2 — expected the escaping path rejected but got: " + escaping;
        var absent = lsp.findDefinition("Missing.java", 1, 1);
        assert "Error: file not found: Missing.java".equals(absent)
                : "R4.2 — expected the absent file rejected but got: " + absent;

        // R4.3 — If the line lies past the last line of the file, then the BC shall reject the
        // request and report the file's line count.
        var pastEnd = lsp.findDefinition("Main.java", 99, 1);
        assert "Error: line 99 lies past the last line, Main.java has 7 lines".equals(pastEnd)
                : "R4.3 — expected the line count reported but got: " + pastEnd;

        // R4.4 — If the line or the column is below one, then the BC shall reject the request.
        var lineZero = lsp.findDefinition("Main.java", 0, 1);
        var columnZero = lsp.findDefinition("Main.java", 1, 0);
        assert lineZero.startsWith("Error: line and column must be at least 1")
                && columnZero.startsWith("Error: line and column must be at least 1")
                : "R4.4 — expected line 0 and column 0 rejected but got: %s / %s".formatted(lineZero, columnZero);
    } finally {
        lsp.releaseServer();
        deleteRecursively(root);
    }
}

static Path workspace() throws IOException {
    var root = Files.createTempDirectory("zunit-lsp");
    Files.writeString(root.resolve("Shape.java"), """
            interface Shape {
                void draw();
            }
            """);
    Files.writeString(root.resolve("Circle.java"), """
            class Circle implements Shape {
                public void draw() {
                    System.out.println("circle");
                }
            }
            """);
    Files.writeString(root.resolve("Main.java"), """
            class Main {
                void run() {
                    Shape shape = new Circle();
                    shape.draw();
                    String name = "main";
                }
            }
            """);
    return root;
}

static String serverCommand(Path log, String... flags) {
    var fake = Path.of("src/test/java/lsp/FakeLanguageServer.java").toAbsolutePath();
    assert Files.exists(fake) : "fake language server not found at " + fake;
    var classpath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(entry -> Path.of(entry).toAbsolutePath().toString())
            .collect(Collectors.joining(File.pathSeparator));
    return String.join(" ", "java", "--source", "25", "--class-path", classpath,
            fake.toString(), log.toString(), String.join(" ", flags)).strip();
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
