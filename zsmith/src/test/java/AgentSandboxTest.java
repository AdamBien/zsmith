import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

import airhacks.zsmith.agent.boundary.Agent;

import static airhacks.zsmith.tools.boundary.SandboxTools.READ_FILE;
import static airhacks.zsmith.tools.boundary.SandboxTools.SEARCH_FILES;

void main() throws IOException {
    var tempDir = Files.createTempDirectory("zunit-sandbox");
    try {
        var selective = new Agent("selective", "You test sandboxes.")
                .withSandbox(tempDir, READ_FILE, SEARCH_FILES);
        assert selective.tools().keySet().equals(Set.of("read_file", "search_files"))
                : "expected exactly the selected sandbox tools but got: " + selective.tools().keySet();

        var full = new Agent("full", "You test sandboxes.").withSandbox(tempDir);
        var allSandboxed = Set.of("read_file", "write_file", "list_files", "list_files_ending", "search_files");
        assert full.tools().keySet().equals(allSandboxed)
                : "no selection should grant all sandboxed tools but got: " + full.tools().keySet();
        assert !full.tools().containsKey("read_any_file")
                : "withSandbox must never grant un-sandboxed file tools";
    } finally {
        Files.delete(tempDir);
    }
}
