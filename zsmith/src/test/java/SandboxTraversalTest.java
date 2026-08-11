import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.tools.boundary.SandboxedFileSystem;
import airhacks.zsmith.tools.control.IgnoredDirectories;

/// Traces tools spec R6.1 and R8.1 - R8.4 — see src/main/java/airhacks/zsmith/tools/package-info.java
///
/// R8.1 runs before any ZCfg.loadBaseConfig so the unconfigured path is exercised
/// verbatim; the later statements reload configuration to override it.

void main() throws IOException {
    var tempDir = Files.createTempDirectory("zunit-traversal");
    try {
        layOutRepositoryLikeTree(tempDir);
        var fs = new SandboxedFileSystem(tempDir);

        builtInDirectoriesAreExcludedWhenUnconfigured(fs);
        excludedDirectoriesArePrunedWholeSubtree(fs);
        listingReturnsRootRelativePaths(fs);
        configuredNamesReplaceTheBuiltInSet(fs);
        anEmptyConfiguredSetTraversesEverything(fs);
    } finally {
        deleteRecursively(tempDir);
    }
}

/// A tree shaped like a checked-out, built repository: sources worth reading and
/// two directories whose contents only cost context.
static void layOutRepositoryLikeTree(Path root) throws IOException {
    Files.writeString(root.resolve("Main.java"), "class Main {}");
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/Helper.java"), "class Helper {}");
    Files.createDirectories(root.resolve(".git/objects"));
    Files.writeString(root.resolve(".git/objects/pack.bin"), "binary noise");
    Files.createDirectories(root.resolve("target/classes"));
    Files.writeString(root.resolve("target/classes/Main.class"), "bytecode");
    Files.createDirectories(root.resolve("vendor"));
    Files.writeString(root.resolve("vendor/lib.java"), "class Lib {}");
}

// R8.1 — The BC shall exclude a built-in set of build and version-control directories from
// every sandboxed traversal.
void builtInDirectoriesAreExcludedWhenUnconfigured(SandboxedFileSystem fs) {
    var listing = fs.listFiles();
    if (listing.contains("pack.bin"))
        throw new AssertionError("R8.1 — expected .git excluded but got: " + listing);
    if (listing.contains("Main.class"))
        throw new AssertionError("R8.1 — expected target excluded but got: " + listing);
    if (!listing.contains("Main.java") || !listing.contains("Helper.java"))
        throw new AssertionError("R8.1 — expected sources retained but got: " + listing);

    var search = fs.searchFiles("binary noise", "");
    if (!"No matches found".equals(search))
        throw new AssertionError("R8.1 — expected search to skip excluded directories but got: " + search);
}

// R8.3 — When a directory is excluded, the BC shall exclude its whole subtree.
void excludedDirectoriesArePrunedWholeSubtree(SandboxedFileSystem fs) {
    var listing = fs.listFiles();
    if (listing.contains(".git"))
        throw new AssertionError("R8.3 — expected nothing below .git but got: " + listing);
    if (listing.contains("target"))
        throw new AssertionError("R8.3 — expected nothing below target but got: " + listing);
}

// R6.1 — The BC shall return every traversable file below the sandbox root as a root-relative path.
void listingReturnsRootRelativePaths(SandboxedFileSystem fs) {
    var listing = fs.listFiles();
    for (var line : listing.split("\n")) {
        if (line.startsWith("/"))
            throw new AssertionError("R6.1 — expected root-relative paths but got: " + line);
    }
    if (!listing.contains("src/Helper.java"))
        throw new AssertionError("R6.1 — expected a nested file as a relative path but got: " + listing);
}

// R8.2 — Where an ignore set is configured, the BC shall exclude it in place of the built-in set.
void configuredNamesReplaceTheBuiltInSet(SandboxedFileSystem fs) {
    System.setProperty(IgnoredDirectories.CONFIGURATION_KEY, "vendor");
    ZCfg.loadBaseConfig("zsmith");

    var listing = fs.listFiles();
    if (listing.contains("lib.java"))
        throw new AssertionError("R8.2 — expected the configured name excluded but got: " + listing);
    if (!listing.contains("pack.bin"))
        throw new AssertionError("R8.2 — expected the built-in set replaced, not extended, but got: " + listing);
}

// R8.4 — If the configured ignore set is empty, then the BC shall traverse every directory
// below the sandbox root.
void anEmptyConfiguredSetTraversesEverything(SandboxedFileSystem fs) {
    System.setProperty(IgnoredDirectories.CONFIGURATION_KEY, "");
    ZCfg.loadBaseConfig("zsmith");

    var listing = fs.listFiles();
    if (!listing.contains("pack.bin") || !listing.contains("Main.class") || !listing.contains("lib.java"))
        throw new AssertionError("R8.4 — expected an empty ignore set to traverse everything but got: " + listing);
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
