package airhacks.zsmith.lsp.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.logging.control.Log;
import airhacks.zsmith.lsp.entity.Position;

/// One code base and the language server that answers for it. Holds the
/// policy the spec promises around a server: start it on the first request
/// and reuse it, report any failure in the call that hit it, and let the
/// next call start afresh. Every public method returns the text an agent
/// reads — a location report or an `Error:` line.
public class Workspace {

    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    static final String UNAVAILABLE = "Error: code navigation is unavailable, no language server launch command is configured";

    static final Map<String, String> LANGUAGE_IDS = Map.of(
            "java", "java", "py", "python", "js", "javascript", "ts", "typescript",
            "go", "go", "rs", "rust", "kt", "kotlin", "c", "c", "cpp", "cpp", "cs", "csharp");

    final Path root;
    final List<String> launchCommand;
    final Duration timeout;
    final Locations locations;
    LanguageServer server;

    /// A blank `launchCommand` means no server is configured: every request
    /// then reports navigation as unavailable instead of failing.
    public Workspace(Path root, String launchCommand, Duration timeout) {
        this.root = root.toAbsolutePath().normalize();
        this.launchCommand = tokenize(launchCommand);
        this.timeout = timeout;
        this.locations = new Locations(this.root);
    }

    public Path root() {
        return this.root;
    }

    public String findSymbols(String query) {
        if (query == null || query.isBlank()) {
            return "Error: query must not be empty";
        }
        return withServer("workspaceSymbolProvider", "workspace/symbol", current -> {
            var symbols = current.request("workspace/symbol", new JSONObject().put("query", query));
            return this.locations.report(this.locations.fromSymbols(symbols, current));
        });
    }

    public String findDefinition(String path, int line, int column) {
        return atPosition(path, line, column, "definitionProvider", "textDocument/definition",
                (current, params) -> this.locations.report(
                        this.locations.fromResult(current.request("textDocument/definition", params))));
    }

    public String findReferences(String path, int line, int column) {
        return atPosition(path, line, column, "referencesProvider", "textDocument/references",
                (current, params) -> {
                    params.put("context", new JSONObject().put("includeDeclaration", true));
                    return this.locations.report(
                            this.locations.fromResult(current.request("textDocument/references", params)));
                });
    }

    public String findImplementations(String path, int line, int column) {
        return atPosition(path, line, column, "implementationProvider", "textDocument/implementation",
                (current, params) -> this.locations.report(
                        this.locations.fromResult(current.request("textDocument/implementation", params))));
    }

    public String findCallers(String path, int line, int column) {
        return atPosition(path, line, column, "callHierarchyProvider", "callHierarchy/incomingCalls",
                (current, params) -> {
                    var item = callHierarchyItem(current, params);
                    if (item == null) {
                        return nothingCallable(path, line, column);
                    }
                    var calls = current.request("callHierarchy/incomingCalls", new JSONObject().put("item", item));
                    return this.locations.report(this.locations.fromCalls(calls,
                            call -> call.getJSONObject("from").getString("uri")));
                });
    }

    public String findCallees(String path, int line, int column) {
        return atPosition(path, line, column, "callHierarchyProvider", "callHierarchy/outgoingCalls",
                (current, params) -> {
                    var item = callHierarchyItem(current, params);
                    if (item == null) {
                        return nothingCallable(path, line, column);
                    }
                    var calls = current.request("callHierarchy/outgoingCalls", new JSONObject().put("item", item));
                    var document = item.getString("uri");
                    return this.locations.report(this.locations.fromCalls(calls, _ -> document));
                });
    }

    public synchronized void release() {
        if (this.server == null) {
            return;
        }
        Log.tool("releasing language server");
        try {
            this.server.shutdown();
        } catch (LanguageServerFailure e) {
            Log.warning("language server did not shut down cleanly: " + e.getMessage());
        } finally {
            discard();
        }
    }

    static JSONObject callHierarchyItem(LanguageServer current, JSONObject params) {
        var items = current.request("textDocument/prepareCallHierarchy", params);
        if (items instanceof JSONArray prepared && prepared.length() > 0 && prepared.get(0) instanceof JSONObject item) {
            return item;
        }
        return null;
    }

    static String nothingCallable(String path, int line, int column) {
        return "Error: nothing callable at %s:%d:%d".formatted(path, line, column);
    }

    /// The shared path of every positional request: validate the cursor,
    /// confine the path, read the file as it is on disk, hand the server that
    /// content for the duration of the query, and close the document again.
    String atPosition(String path, int line, int column, String provider, String method,
                      BiFunction<LanguageServer, JSONObject, String> query) {
        Position position;
        Path file;
        try {
            position = new Position(path, line, column);
            file = resolve(path);
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            return "Error: cannot read %s: %s".formatted(path, e.getMessage());
        }
        var lineCount = (int) content.lines().count();
        if (line > lineCount) {
            return "Error: line %d lies past the last line, %s has %d lines".formatted(line, path, lineCount);
        }
        var uri = file.toUri().toString();
        return withServer(provider, method, current -> {
            current.openDocument(uri, languageId(file), content);
            try {
                return query.apply(current, positionParams(uri, position));
            } finally {
                closeQuietly(current, uri);
            }
        });
    }

    synchronized String withServer(String provider, String method, Function<LanguageServer, String> action) {
        if (this.launchCommand.isEmpty()) {
            return UNAVAILABLE;
        }
        try {
            var current = connect();
            if (!current.capabilities().supports(provider)) {
                return "Error: the language server does not support " + method;
            }
            return action.apply(current);
        } catch (LanguageServerFailure e) {
            Log.warning("language server failure: " + e.getMessage());
            discard();
            return "Error: " + e.getMessage() + "; a fresh language server starts on the next request";
        }
    }

    LanguageServer connect() {
        if (this.server == null || !this.server.alive()) {
            discard();
            this.server = LanguageServer.start(this.launchCommand, this.root, this.timeout);
        }
        return this.server;
    }

    void discard() {
        if (this.server != null) {
            this.server.destroy();
            this.server = null;
        }
    }

    static void closeQuietly(LanguageServer current, String uri) {
        if (!current.alive()) {
            return;
        }
        try {
            current.closeDocument(uri);
        } catch (LanguageServerFailure e) {
            Log.debug("could not close document: " + e.getMessage());
        }
    }

    /// Confinement mirrors the sandbox rules: relative, inside the root after
    /// normalization, and an existing regular file.
    Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("path must not be empty");
        }
        var candidate = Path.of(relativePath);
        if (candidate.isAbsolute()) {
            throw new IllegalArgumentException("path must be relative to the workspace root: " + relativePath);
        }
        var resolved = this.root.resolve(candidate).normalize();
        if (!resolved.startsWith(this.root)) {
            throw new IllegalArgumentException("path escapes the workspace root: " + relativePath);
        }
        if (!Files.isRegularFile(resolved)) {
            throw new IllegalArgumentException("file not found: " + relativePath);
        }
        return resolved;
    }

    static JSONObject positionParams(String uri, Position position) {
        return new JSONObject()
                .put("textDocument", new JSONObject().put("uri", uri))
                .put("position", new JSONObject()
                        .put("line", position.zeroBasedLine())
                        .put("character", position.zeroBasedColumn()));
    }

    static String languageId(Path file) {
        var name = file.getFileName().toString();
        var dot = name.lastIndexOf('.');
        var extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
        return LANGUAGE_IDS.getOrDefault(extension, extension.isEmpty() ? "plaintext" : extension);
    }

    static List<String> tokenize(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        return Arrays.stream(command.trim().split("\\s+")).toList();
    }
}
