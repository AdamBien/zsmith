package airhacks.zsmith.tools.control;

import airhacks.zsmith.tools.boundary.Tool;
import java.nio.file.Path;

import airhacks.zsmith.tools.boundary.SandboxedFileSystem;

public interface ListFilesTool {

    static Tool of(String sandboxPath) {
        return create(new SandboxedFileSystem(Path.of(sandboxPath)));
    }

    static Tool create(SandboxedFileSystem fs) {
        return Tool.of(
                "list_files",
                "Lists all files within the sandbox directory",
                Tool.emptySchema(),
                input -> fs.listFiles());
    }
}
