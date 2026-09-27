import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import airhacks.zsmith.lsp.boundary.Lsp;

/// Traces lsp spec R3.1, R3.2 — see src/main/java/airhacks/zsmith/lsp/package-info.java
/// The language server is src/test/java/lsp/FakeLanguageServer.java, answering from the workspace files.

void main() throws IOException {
    var root = workspace();
    var lsp = Lsp.of(root, serverCommand(root.resolve("server.log")), Duration.ofSeconds(20));
    try {
        // R3.1 — When a name query is supplied, the BC shall return the location of every
        // workspace symbol the server matches to it.
        var found = lsp.findSymbols("draw");
        var expected = "Circle.java:2:17: public void draw() {\nShape.java:2:10: void draw();";
        assert expected.equals(found) : "R3.1 — expected every matching symbol location but got: " + found;

        // R3.2 — If the query is empty, then the BC shall reject the request.
        var empty = lsp.findSymbols("");
        var blank = lsp.findSymbols("   ");
        assert "Error: query must not be empty".equals(empty) && "Error: query must not be empty".equals(blank)
                : "R3.2 — expected the empty query rejected but got: %s / %s".formatted(empty, blank);
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
