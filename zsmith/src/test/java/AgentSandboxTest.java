import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

import airhacks.zsmith.agent.boundary.Agent;

import static airhacks.zsmith.tools.boundary.SandboxTools.READ_FILE;
import static airhacks.zsmith.tools.boundary.SandboxTools.SEARCH_FILES;

/// Traces tools spec R1.2, R1.3, R1.4 — see src/main/java/airhacks/zsmith/tools/package-info.java

void main() throws IOException {
    var tempDir = Files.createTempDirectory("zunit-sandbox");
    try {
        // R1.2 — When a sandbox root and a selection of file handlers are supplied, the BC shall
        // bind exactly the selected handlers to that root.
        var selective = new Agent("selective", "You test sandboxes.")
                .withSandbox(tempDir, READ_FILE, SEARCH_FILES);
        assert selective.tools().keySet().equals(Set.of("read_file", "search_files"))
                : "R1.2 — expected exactly the selected sandbox tools but got: " + selective.tools().keySet();

        // R1.3 — When a sandbox root is supplied without a selection, the BC shall bind every
        // sandboxed file handler to that root.
        var full = new Agent("full", "You test sandboxes.").withSandbox(tempDir);
        var allSandboxed = Set.of("read_file", "write_file", "edit_file", "list_files", "list_files_ending", "find_files", "search_files");
        assert full.tools().keySet().equals(allSandboxed)
                : "R1.3 — no selection should grant all sandboxed tools but got: " + full.tools().keySet();

        // R1.4 — If a sandboxed tool set is requested, then the BC shall withhold every
        // unconfined file handler from it.
        assert !full.tools().containsKey("read_any_file")
                : "R1.4 — withSandbox must never grant un-sandboxed file tools";
    } finally {
        Files.delete(tempDir);
    }
}
