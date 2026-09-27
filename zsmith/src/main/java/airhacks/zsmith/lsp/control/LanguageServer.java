package airhacks.zsmith.lsp.control;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.logging.control.Log;
import airhacks.zsmith.lsp.entity.ServerCapabilities;

/// One running language server: the child process, the connection over its
/// standard streams and the capabilities it declared. Created initialized —
/// the handshake is part of [#start], so a caller never holds a server that
/// is not ready for navigation requests.
public class LanguageServer {

    final Process process;
    final JsonRpcConnection connection;
    final Duration timeout;
    ServerCapabilities capabilities = ServerCapabilities.none();

    LanguageServer(Process process, JsonRpcConnection connection, Duration timeout) {
        this.process = process;
        this.connection = connection;
        this.timeout = timeout;
    }

    public static LanguageServer start(List<String> command, Path root, Duration timeout) {
        Log.tool("starting language server: " + String.join(" ", command));
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .directory(root.toFile())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException e) {
            throw new LanguageServerFailure("the language server could not be started: " + e.getMessage(), e);
        }
        var connection = new JsonRpcConnection(process.getInputStream(), process.getOutputStream(), timeout);
        var server = new LanguageServer(process, connection, timeout);
        try {
            server.initialize(root);
        } catch (LanguageServerFailure e) {
            server.destroy();
            throw e;
        }
        return server;
    }

    void initialize(Path root) {
        var rootUri = directoryUri(root);
        var params = new JSONObject()
                .put("processId", ProcessHandle.current().pid())
                .put("clientInfo", new JSONObject().put("name", "zsmith"))
                .put("rootUri", rootUri)
                .put("workspaceFolders", new JSONArray().put(new JSONObject()
                        .put("uri", rootUri)
                        .put("name", String.valueOf(root.getFileName()))))
                .put("capabilities", clientCapabilities());
        var result = this.connection.request("initialize", params);
        var declared = result instanceof JSONObject initialized
                ? initialized.optJSONObject("capabilities", new JSONObject())
                : new JSONObject();
        this.capabilities = new ServerCapabilities(declared);
        this.connection.notify("initialized", new JSONObject());
    }

    static JSONObject clientCapabilities() {
        return new JSONObject()
                .put("workspace", new JSONObject()
                        .put("workspaceFolders", true)
                        .put("symbol", new JSONObject()))
                .put("textDocument", new JSONObject()
                        .put("definition", new JSONObject())
                        .put("references", new JSONObject())
                        .put("implementation", new JSONObject())
                        .put("callHierarchy", new JSONObject()));
    }

    /// A directory URI without the trailing slash `Path.toUri()` appends —
    /// servers compare root URIs textually against the ones they receive later.
    static String directoryUri(Path directory) {
        var uri = directory.toUri().toString();
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    public ServerCapabilities capabilities() {
        return this.capabilities;
    }

    public boolean alive() {
        return this.process.isAlive();
    }

    public Object request(String method, JSONObject params) {
        return this.connection.request(method, params);
    }

    public void openDocument(String uri, String languageId, String text) {
        this.connection.notify("textDocument/didOpen", new JSONObject()
                .put("textDocument", new JSONObject()
                        .put("uri", uri)
                        .put("languageId", languageId)
                        .put("version", 1)
                        .put("text", text)));
    }

    public void closeDocument(String uri) {
        this.connection.notify("textDocument/didClose", new JSONObject()
                .put("textDocument", new JSONObject().put("uri", uri)));
    }

    /// The orderly end the protocol prescribes: a `shutdown` request, then an
    /// `exit` notification, then waiting for the process — and the hammer if
    /// the server ignores all of it.
    public void shutdown() {
        try {
            this.connection.request("shutdown", null);
            this.connection.notify("exit", null);
            if (!this.process.waitFor(this.timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                Log.warning("language server ignored exit, destroying it");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            destroy();
        }
    }

    public void destroy() {
        this.connection.close();
        this.process.destroyForcibly();
    }
}
