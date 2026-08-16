package airhacks.zsmith.tools.boundary;

import airhacks.zsmith.tools.control.ToolHandler;

/// The published contract for tools implemented outside the framework.
/// Extending the internal handler keeps a custom tool interchangeable with
/// every built-in one, so `Agent#withTool` accepts it unchanged.
public interface Tool extends ToolHandler {
}
