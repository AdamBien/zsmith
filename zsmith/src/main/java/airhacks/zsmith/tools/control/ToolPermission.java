package airhacks.zsmith.tools.control;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.logging.control.Log;

public enum ToolPermission {

    ALLOW,
    DENY,
    CONFIRM;

    public static final String PREFIX = "tools.permissions.";
    static final String DEFAULT_KEY = PREFIX + "default";

    public static ToolPermission resolve(String toolName) {
        var value = ZCfg.string(PREFIX + toolName);
        if (value != null) {
            return parse(value);
        }
        var defaultValue = ZCfg.string(DEFAULT_KEY);
        if (defaultValue != null) {
            return parse(defaultValue);
        }
        return CONFIRM;
    }

    /// Persists this decision in the agent's own configuration, where `resolve` finds it on
    /// the next call.
    public void store(String agentName, String toolName) {
        ZCfg.storeAgentProperty(agentName, PREFIX + toolName, name().toLowerCase());
        Log.tool("tool permission persisted: " + toolName + " = " + name().toLowerCase());
    }

    static ToolPermission parse(String value) {
        return valueOf(value.strip().toUpperCase());
    }
}
