package airhacks.zsmith.lsp.boundary;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.lsp.control.NavigationTools;
import airhacks.zsmith.lsp.control.Workspace;
import airhacks.zsmith.tools.boundary.Tool;

/// Code navigation for one workspace through a language server. Every
/// navigation method returns the text an agent reads: locations as
/// `<path>:<line>:<column>: <source line>` or an `Error:` line. The server is
/// started on the first request and lives until [#releaseServer].
public record Lsp(Workspace workspace) {

    public static final String COMMAND_KEY = "lsp.command";
    public static final String TIMEOUT_KEY = "lsp.timeout.seconds";

    public static Lsp of(Path root, String launchCommand) {
        return of(root, launchCommand, Duration.ofSeconds(Workspace.DEFAULT_TIMEOUT_SECONDS));
    }

    public static Lsp of(Path root, String launchCommand, Duration timeout) {
        return new Lsp(new Workspace(root, launchCommand, timeout));
    }

    /// A workspace without a language server: every request reports
    /// navigation as unavailable.
    public static Lsp unavailable(Path root) {
        return of(root, "");
    }

    /// The agent's sandbox as the workspace, the server from `lsp.command`
    /// and the request timeout from `lsp.timeout.seconds`.
    public static Lsp fromConfig(String agentName) {
        return of(ZCfg.sandboxPath(agentName),
                ZCfg.string(COMMAND_KEY, ""),
                Duration.ofSeconds(ZCfg.integer(TIMEOUT_KEY, Workspace.DEFAULT_TIMEOUT_SECONDS)));
    }

    public List<Tool> tools() {
        return NavigationTools.of(this);
    }

    public String findSymbols(String query) {
        return this.workspace.findSymbols(query);
    }

    public String findDefinition(String path, int line, int column) {
        return this.workspace.findDefinition(path, line, column);
    }

    public String findReferences(String path, int line, int column) {
        return this.workspace.findReferences(path, line, column);
    }

    public String findImplementations(String path, int line, int column) {
        return this.workspace.findImplementations(path, line, column);
    }

    public String findCallers(String path, int line, int column) {
        return this.workspace.findCallers(path, line, column);
    }

    public String findCallees(String path, int line, int column) {
        return this.workspace.findCallees(path, line, column);
    }

    public void releaseServer() {
        this.workspace.release();
    }
}
