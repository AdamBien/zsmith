import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import airhacks.zsmith.lsp.boundary.Lsp;

/// Traces lsp spec R5.1, R6.1, R7.1, R8.1, R8.2, R8.3 — see src/main/java/airhacks/zsmith/lsp/package-info.java
/// The language server is src/test/java/lsp/FakeLanguageServer.java, answering from the workspace files.

void main() throws IOException {
    var root = workspace();
    var lsp = Lsp.of(root, serverCommand(root.resolve("server.log")), Duration.ofSeconds(20));
    try {
        // R5.1 — When a position is supplied, the BC shall return every location where the symbol at it is defined.
        var definition = lsp.findDefinition("Main.java", 4, 15);
        assert "Circle.java:2:17: public void draw() {\nShape.java:2:10: void draw();".equals(definition)
                : "R5.1 — expected both declarations of draw but got: " + definition;

        // R6.1 — When a position is supplied, the BC shall return every location where the symbol
        // at it is used, including its declaration.
        var references = lsp.findReferences("Main.java", 4, 15);
        assert "Circle.java:2:17: public void draw() {\nMain.java:4:15: shape.draw();\nShape.java:2:10: void draw();".equals(references)
                : "R6.1 — expected every use of draw including its declarations but got: " + references;

        // R7.1 — When a position is supplied, the BC shall return every location implementing the symbol at it.
        var implementations = lsp.findImplementations("Shape.java", 2, 10);
        assert "Circle.java:2:17: public void draw() {".equals(implementations)
                : "R7.1 — expected the implementing method but got: " + implementations;

        // R8.1 — When a position on a callable symbol is supplied, the BC shall return the location of every call into it.
        var callers = lsp.findCallers("Shape.java", 2, 10);
        assert "Main.java:4:15: shape.draw();".equals(callers)
                : "R8.1 — expected the call site of draw but got: " + callers;

        // R8.2 — When a position on a callable symbol is supplied, the BC shall return the location of every call it makes.
        var callees = lsp.findCallees("Main.java", 2, 10);
        assert "Main.java:4:15: shape.draw();".equals(callees)
                : "R8.2 — expected the call run makes but got: " + callees;

        // R8.3 — If the position names no callable symbol, then the BC shall report that nothing callable is there.
        var nothing = lsp.findCallers("Main.java", 3, 15);
        assert "Error: nothing callable at Main.java:3:15".equals(nothing)
                : "R8.3 — expected nothing callable reported but got: " + nothing;
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
