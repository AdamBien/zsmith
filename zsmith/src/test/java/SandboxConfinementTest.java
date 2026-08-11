import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;

/// Traces tools spec R3.1 - R3.5 — see src/main/java/airhacks/zsmith/tools/package-info.java

void main() throws IOException {
    var tempDir = Files.createTempDirectory("zunit-confinement");
    var outside = Files.createTempDirectory("zunit-outside");
    try {
        Files.writeString(tempDir.resolve("inside.txt"), "reachable");
        Files.writeString(outside.resolve("secret.txt"), "unreachable");
        var fs = new SandboxedFileSystem(tempDir);

        relativePathsBelowTheRootResolve(fs);
        absolutePathsAreRejected(fs);
        parentDirectorySegmentsAreRejected(fs);
        emptyAndNullBytePathsAreRejected(fs);
        symlinksLeavingTheRootAreRejected(fs, tempDir, outside);
    } finally {
        deleteRecursively(tempDir);
        deleteRecursively(outside);
    }
}

// R3.1 — When a sandboxed operation receives a relative path below the sandbox root, the BC
// shall resolve it against that root.
void relativePathsBelowTheRootResolve(SandboxedFileSystem fs) {
    var content = fs.readFile("inside.txt");
    if (!"reachable".equals(content))
        throw new AssertionError("R3.1 — expected the sandboxed file's content but got: " + content);
}

// R3.2 — If a sandboxed path is absolute, then the BC shall reject the operation.
void absolutePathsAreRejected(SandboxedFileSystem fs) {
    rejects("R3.2", fs, "/etc/passwd");
}

// R3.3 — If a sandboxed path contains a parent-directory segment, then the BC shall reject
// the operation.
void parentDirectorySegmentsAreRejected(SandboxedFileSystem fs) {
    rejects("R3.3", fs, "../secret.txt");
    rejects("R3.3", fs, "sub/../../secret.txt");
}

// R3.4 — If a sandboxed path is empty or carries a null character, then the BC shall reject
// the operation.
void emptyAndNullBytePathsAreRejected(SandboxedFileSystem fs) {
    rejects("R3.4", fs, "");
    rejects("R3.4", fs, "inside.txt\0");
}

// R3.5 — If a sandboxed path resolves through a symbolic link leading outside the sandbox
// root, then the BC shall reject the operation.
void symlinksLeavingTheRootAreRejected(SandboxedFileSystem fs, Path root, Path outside) throws IOException {
    try {
        Files.createSymbolicLink(root.resolve("escape.txt"), outside.resolve("secret.txt"));
    } catch (UnsupportedOperationException | IOException unsupported) {
        return; // the platform forbids symlink creation — nothing to confine here
    }
    rejects("R3.5", fs, "escape.txt");
}

/// A rejection is an IllegalArgumentException; returning content or a plain error string
/// would mean the path was resolved after all.
static void rejects(String requirement, SandboxedFileSystem fs, String path) {
    try {
        var result = fs.readFile(path);
        throw new AssertionError(requirement + " — expected rejection of '" + path + "' but got: " + result);
    } catch (IllegalArgumentException expected) {
    }
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
