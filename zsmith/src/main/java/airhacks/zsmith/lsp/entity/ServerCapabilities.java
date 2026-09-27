package airhacks.zsmith.lsp.entity;

import airhacks.zsmith.json.JSONObject;

/// What the server declared during initialization. A provider counts as
/// present when its key holds anything but `false` or `null` — the protocol
/// allows a bare `true` as well as an options object.
public record ServerCapabilities(JSONObject declared) {

    public static ServerCapabilities none() {
        return new ServerCapabilities(new JSONObject());
    }

    public boolean supports(String provider) {
        if (!this.declared.has(provider) || this.declared.isNull(provider)) {
            return false;
        }
        return !(this.declared.get(provider) instanceof Boolean flag) || flag;
    }

    public boolean resolvesWorkspaceSymbols() {
        var provider = this.declared.optJSONObject("workspaceSymbolProvider");
        return provider != null && provider.optBoolean("resolveProvider");
    }
}
