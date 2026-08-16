package airhacks.zsmith.tools.boundary;

import java.util.List;
import java.util.stream.Stream;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.tools.control.ReadAnyFileTool;
import airhacks.zsmith.tools.control.WriteAnyFileTool;

/**
 * Predefined groupings of {@link Tool}s for common agent capabilities.
 *
 * <p>Separates the concern of <em>which tools exist</em> ({@link Tools}) from
 * <em>which tools belong together for a given use case</em>. Agents compose
 * capabilities by selecting a profile rather than cherry-picking individual
 * tools, keeping the wiring in {@link airhacks.zsmith.agent.boundary.Agent}
 * intention-revealing (e.g. {@code agent.withTools(ToolProfiles.userIO())}).
 *
 * <p>Implemented as an interface with constant fields so it acts as a
 * pure namespace — no instantiation, no state, just curated lists.
 */
public interface ToolProfiles {

    static List<Tool> userIO() {
        return List.of(Tools.USER_MESSAGE, Tools.USER_QUESTION, Tools.USER_CONFIRMATION);
    }

    static List<Tool> clipboard() {
        return List.of(Tools.READ_CLIPBOARD, Tools.WRITE_CLIPBOARD);
    }

    static List<Tool> fileIO(String agentName) {
        var sandbox = new SandboxedFileSystem(ZCfg.sandboxPath(agentName));
        var sandboxed = Stream.of(SandboxTools.values())
                .map(tool -> tool.create(sandbox));
        return Stream.concat(sandboxed, Stream.of(ReadAnyFileTool.create(), WriteAnyFileTool.create()))
                .toList();
    }

    static List<Tool> all() {
        return List.of(Tools.values());
    }
}
