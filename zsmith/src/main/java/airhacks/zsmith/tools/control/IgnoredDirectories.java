package airhacks.zsmith.tools.control;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import airhacks.zsmith.configuration.control.ZCfg;

/// Directory names skipped by every sandboxed traversal.
///
/// A sandbox rooted at a checked-out repository otherwise walks version-control
/// objects and build output, which is both the slowest and the least informative
/// part of the tree.
public interface IgnoredDirectories {

    String CONFIGURATION_KEY = "tools.sandbox.ignore";

    Set<String> BUILT_IN = Set.of(
            ".git", ".hg", ".svn",
            "target", "build", "out", "bin", "zbo",
            "node_modules");

    /// The configured names when `tools.sandbox.ignore` is set — a comma separated
    /// list replacing, not extending, [#BUILT_IN] — otherwise the built-in names.
    /// Configuring the key to an empty value traverses everything.
    static Set<String> resolve() {
        var configured = configuredNames();
        if (configured == null) {
            return BUILT_IN;
        }
        return Stream.of(configured.split(","))
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /// Null rather than a thrown state error: a [airhacks.zsmith.tools.boundary.SandboxedFileSystem]
    /// constructed directly, without an agent having loaded configuration, still traverses.
    private static String configuredNames() {
        try {
            return ZCfg.string(CONFIGURATION_KEY);
        } catch (IllegalStateException _) {
            return null;
        }
    }
}
