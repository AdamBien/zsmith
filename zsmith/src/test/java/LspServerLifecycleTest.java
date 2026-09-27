import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import airhacks.zsmith.lsp.boundary.Lsp;

/// Traces lsp spec R2.1, R2.2, R2.3, R2.4, R2.5, R2.6, R2.7, R2.8, R2.9 — see src/main/java/airhacks/zsmith/lsp/package-info.java
/// The language server is src/test/java/lsp/FakeLanguageServer.java; it logs every method it receives.

void main() throws IOException {
    var root = workspace();
    var log = root.resolve("server.log");
    try {
        // R2.2 — If no launch command is configured, then the BC shall report navigation as unavailable rather than fail.
        var unavailable = Lsp.unavailable(root).findSymbols("draw");
        assert unavailable.startsWith("Error: code navigation is unavailable")
                : "R2.2 — expected navigation reported as unavailable but got: " + unavailable;

        // R2.6 — If the server cannot be started, then the BC shall report the cause in the request's result.
        var unstartable = Lsp.of(root, "/nonexistent/language-server", Duration.ofSeconds(5)).findSymbols("draw");
        assert unstartable.startsWith("Error: the language server could not be started")
                : "R2.6 — expected the start failure in the result but got: " + unstartable;

        var lsp = Lsp.of(root, serverCommand(log), Duration.ofSeconds(8));
        try {
            // R2.1 — When the first navigation request of a workspace arrives, the BC shall start the
            // language server from the configured launch command and reuse that server for every later request.
            var first = lsp.findSymbols("draw");
            var second = lsp.findSymbols("Circle");
            assert first.contains("Shape.java:2:10") && second.contains("Circle.java:1:7")
                    : "R2.1 — expected both requests answered but got: %s / %s".formatted(first, second);
            assert count(log, "started") == 1
                    : "R2.1 — expected one server start for two requests but the log shows: " + Files.readAllLines(log);

            // R2.3 — When a server is started, the BC shall complete the protocol's initialization
            // handshake, rooted at the workspace root, before sending it any navigation request.
            var entries = Files.readAllLines(log);
            assert entries.indexOf("initialize") < entries.indexOf("initialized")
                    && entries.indexOf("initialized") < entries.indexOf("workspace/symbol")
                    : "R2.3 — expected initialize, initialized, then the query but the log shows: " + entries;

            // R2.4 — When the server initiates a request, the BC shall answer it, and when the server
            // sends a notification, the BC shall accept it.
            assert entries.contains("response:srv-1")
                    : "R2.4 — expected the server-initiated request to be answered but the log shows: " + entries;

            // R2.7 — If the server exits or its connection breaks during a request, then the BC shall
            // report the failure in that request's result and start a fresh server on the next request.
            var crashed = lsp.findSymbols("crash");
            assert crashed.startsWith("Error:") && crashed.contains("a fresh language server starts on the next request")
                    : "R2.7 — expected the crash reported in the result but got: " + crashed;
            var recovered = lsp.findSymbols("draw");
            assert recovered.contains("Shape.java:2:10")
                    : "R2.7 — expected the next request to succeed on a fresh server but got: " + recovered;
            assert count(log, "started") == 2
                    : "R2.7 — expected a second server start but the log shows: " + Files.readAllLines(log);

            // R2.8 — If a request outlives its timeout, then the BC shall report the timeout in its
            // result, discard the server and start a fresh one on the next request.
            var hung = lsp.findSymbols("hang");
            assert hung.contains("did not answer workspace/symbol within 8s")
                    && hung.contains("a fresh language server starts on the next request")
                    : "R2.8 — expected the timeout reported in the result but got: " + hung;
            var afterTimeout = lsp.findSymbols("draw");
            assert afterTimeout.contains("Shape.java:2:10") && count(log, "started") == 3
                    : "R2.8 — expected a third server to answer but got: %s, log: %s".formatted(afterTimeout, Files.readAllLines(log));

            // R2.9 — When the server is released, the BC shall ask it to shut down and then to exit.
            lsp.releaseServer();
            entries = Files.readAllLines(log);
            assert entries.contains("shutdown") && entries.lastIndexOf("exit") > entries.lastIndexOf("shutdown")
                    : "R2.9 — expected shutdown followed by exit but the log shows: " + entries;
        } finally {
            lsp.releaseServer();
        }

        // R2.5 — If the server does not provide a requested operation, then the BC shall report that
        // operation as unsupported by the server.
        var limited = Lsp.of(root, serverCommand(root.resolve("limited.log"), "--without-implementation"), Duration.ofSeconds(20));
        try {
            var unsupported = limited.findImplementations("Main.java", 4, 15);
            assert "Error: the language server does not support textDocument/implementation".equals(unsupported)
                    : "R2.5 — expected the operation reported as unsupported but got: " + unsupported;
        } finally {
            limited.releaseServer();
        }
    } finally {
        deleteRecursively(root);
    }
}

static long count(Path log, String entry) throws IOException {
    return Files.readAllLines(log).stream().filter(entry::equals).count();
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
