package airhacks.zsmith.tools.boundary;

import java.util.function.Function;

import airhacks.zsmith.tools.control.EditFileTool;
import airhacks.zsmith.tools.control.FindFilesTool;
import airhacks.zsmith.tools.control.ListFilesEndingTool;
import airhacks.zsmith.tools.control.ListFilesTool;
import airhacks.zsmith.tools.control.ReadFileTool;
import airhacks.zsmith.tools.control.SearchFilesTool;
import airhacks.zsmith.tools.control.WriteFileTool;

/**
 * Sandboxed file tools. Unlike {@link Tools}, these cannot be enum-backed
 * singletons — each instance is bound to a {@link SandboxedFileSystem} at
 * creation time. Selected by name via
 * {@code agent.withSandbox(root, READ_FILE, SEARCH_FILES)}.
 */
public enum SandboxTools {

    READ_FILE(ReadFileTool::create),
    WRITE_FILE(WriteFileTool::create),
    EDIT_FILE(EditFileTool::create),
    LIST_FILES(ListFilesTool::create),
    LIST_FILES_ENDING(ListFilesEndingTool::create),
    FIND_FILES(FindFilesTool::create),
    SEARCH_FILES(SearchFilesTool::create);

    final Function<SandboxedFileSystem, Tool> factory;

    SandboxTools(Function<SandboxedFileSystem, Tool> factory) {
        this.factory = factory;
    }

    public Tool create(SandboxedFileSystem sandbox) {
        return this.factory.apply(sandbox);
    }
}
