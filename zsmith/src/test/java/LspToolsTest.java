import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.lsp.boundary.Lsp;
import airhacks.zsmith.tools.boundary.Tool;

/// Traces lsp spec R1.1, R1.2, R1.3, R1.4, R1.5, R1.6 — see src/main/java/airhacks/zsmith/lsp/package-info.java
/// The language server is src/test/java/lsp/FakeLanguageServer.java, answering from the workspace files.

void main() throws IOException {
    var root = workspace();
    var lsp = Lsp.of(root, serverCommand(root.resolve("server.log")), Duration.ofSeconds(20));
    try {
        // R1.1 — When a workspace root is supplied, the BC shall expose every navigation operation
        // as a named handler fulfilling the published tool contract.
        var tools = lsp.tools();
        var names = tools.stream().map(Tool::toolName).toList();
        var expectedNames = List.of("find_symbols", "find_definition", "find_references",
                "find_implementations", "find_callers", "find_callees");
        assert expectedNames.equals(names) : "R1.1 — expected handlers %s but got %s".formatted(expectedNames, names);
        var definitionTool = tools.stream().filter(tool -> "find_definition".equals(tool.toolName())).findFirst().orElseThrow();
        var properties = definitionTool.inputSchema().getJSONObject("properties");
        assert properties.has("path") && properties.has("line") && properties.has("column")
                : "R1.1 — expected path, line and column in the input schema but got: " + properties;
        var viaTool = definitionTool.execute(new JSONObject().put("path", "Main.java").put("line", 4).put("column", 15));
        var viaBoundary = lsp.findDefinition("Main.java", 4, 15);
        assert viaTool.equals(viaBoundary) && viaTool.contains("Shape.java:2:10")
                : "R1.1 — expected the handler to answer like the boundary but got: " + viaTool;

        // R1.2 — The BC shall report each location as its root-relative path, one-based line,
        // one-based column and the text of that source line.
        var references = lsp.findReferences("Main.java", 4, 15);
        var lines = references.lines().toList();
        assert lines.contains("Circle.java:2:17: public void draw() {")
                : "R1.2 — expected <path>:<line>:<column>: <source line> but got: " + references;

        // R1.3 — The BC shall return locations in a stable order.
        var ordered = List.of(
                "Circle.java:2:17: public void draw() {",
                "Main.java:4:15: shape.draw();",
                "Shape.java:2:10: void draw();");
        assert ordered.equals(lines) : "R1.3 — expected locations ordered by path, line, column but got: " + lines;

        // R1.4 — If the locations exceed the reportable limit, then the BC shall return the limit
        // and state that the result was truncated.
        var many = new StringBuilder("class Many {\n");
        for (var i = 0; i < 201; i++) {
            many.append("    void m").append(i).append("() { many(); }\n");
        }
        many.append("}\n");
        Files.writeString(root.resolve("Many.java"), many.toString());
        var truncated = lsp.findReferences("Many.java", 2, "    void m0() { many(); }".indexOf("many") + 1).lines().toList();
        assert truncated.size() == 201 && "... truncated at 200 locations".equals(truncated.getLast())
                : "R1.4 — expected 200 locations plus a truncation note but got %d lines ending with: %s"
                        .formatted(truncated.size(), truncated.getLast());

        // R1.5 — If no location is found, then the BC shall report that none was found.
        var none = lsp.findSymbols("Nothing");
        assert "No locations found".equals(none) : "R1.5 — expected 'No locations found' but got: " + none;

        // R1.6 — If the server names a location outside the workspace root, then the BC shall
        // report it as the server names it, without line text.
        var outside = lsp.findDefinition("Main.java", 5, 9);
        assert "jdt://contents/java.base/java.lang/String.class:1:1".equals(outside)
                : "R1.6 — expected the server's own URI without line text but got: " + outside;
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
