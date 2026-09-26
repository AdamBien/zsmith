package airhacks.zsmith.agent.control;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import airhacks.zsmith.json.JSONArray;

import airhacks.zsmith.correlation.control.Correlations;
import airhacks.zsmith.correlation.entity.Correlation;
import airhacks.zsmith.logging.control.Log;
import airhacks.zsmith.tools.boundary.Tool;
import airhacks.zsmith.tools.control.Console;
import airhacks.zsmith.tools.control.ToolPermission;
import airhacks.zsmith.tools.entity.ToolInvocationEvent;
import airhacks.zsmith.tools.entity.ToolResult;
import airhacks.zsmith.tools.entity.ToolUse;

/// Executes the tool uses of one turn on behalf of the named agent: resolves permission,
/// asks the user where configuration says so, runs parallel-capable tools concurrently
/// and the rest in order, and turns every outcome into a result the LLM can read.
public record ToolInvocations(String agentName, Map<String, Tool> tools) {

    /// The tool uses of one turn split by how they may run. A tool that is not registered
    /// counts as sequential so its "not available" result is produced in order with the rest.
    public record Plan(List<ToolUse> parallel, List<ToolUse> sequential) {
    }

    public Plan plan(List<ToolUse> toolUses) {
        var parallel = toolUses.stream()
                .filter(toolUse -> {
                    var tool = this.tools.get(toolUse.name());
                    return tool != null && tool.parallel();
                })
                .toList();
        var sequential = toolUses.stream()
                .filter(toolUse -> !parallel.contains(toolUse))
                .toList();
        return new Plan(parallel, sequential);
    }

    /// Results arrive in plan order — parallel first, then sequential — so the content block
    /// the LLM receives is stable regardless of which worker finished first.
    public JSONArray execute(Plan plan, Correlation correlation) {
        var results = new JSONArray();
        if (!plan.parallel().isEmpty()) {
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = plan.parallel().stream()
                        .map(toolUse -> Map.entry(toolUse, executor.submit(() -> execute(toolUse, correlation))))
                        .toList();
                for (var entry : futures) {
                    try {
                        results.put(entry.getValue().get().toContentBlock());
                    } catch (Exception e) {
                        results.put(ToolResult.error(entry.getKey().id(), e.getMessage()).toContentBlock());
                    }
                }
            }
        }
        for (var toolUse : plan.sequential()) {
            results.put(execute(toolUse, correlation).toContentBlock());
        }
        return results;
    }

    /// Takes the correlation as an argument rather than reading it from the ambient scope:
    /// parallel-capable tools are submitted to a virtual-thread executor, which does not
    /// inherit scoped value bindings. It is re-bound around the tool body so that whatever
    /// the tool reaches — episodic memory, a nested agent, another LLM call — records the
    /// same run, on whichever thread it ends up.
    public ToolResult execute(ToolUse toolUse, Correlation correlation) {
        var event = ToolInvocationEvent.of(this.agentName, correlation, toolUse);
        event.begin();
        try {
            var tool = this.tools.get(toolUse.name());
            if (tool == null) {
                Log.tool("tool not available: " + toolUse.name());
                event.outcome = "not_available";
                return ToolResult.error(toolUse.id(), "Tool not available: " + toolUse.name());
            }
            var permission = ToolPermission.resolve(toolUse.name());
            if (permission == ToolPermission.DENY) {
                Log.tool("tool denied: " + toolUse.name());
                event.outcome = "denied";
                return ToolResult.error(toolUse.id(), "Denied: tool not permitted by agent configuration");
            }
            if (permission == ToolPermission.CONFIRM) {
                var denial = confirm(toolUse);
                if (denial != null) {
                    event.outcome = "denied";
                    return ToolResult.error(toolUse.id(), denial);
                }
            }
            try {
                Log.tool("→ %s %s".formatted(toolUse.name(), Log.truncate(String.valueOf(toolUse.input()), 200)));
                var start = System.currentTimeMillis();
                var result = ScopedValue.where(Correlations.CURRENT, correlation)
                        .call(() -> tool.execute(toolUse.input()));
                var duration = System.currentTimeMillis() - start;
                Log.tool("← %s %s".formatted(toolUse.name(), result == null ? "<null>" : Log.truncate(result, 200)));
                Log.toolEnd("%s %dms".formatted(toolUse.name(), duration));
                event.outcome = "success";
                event.resultSize = result == null ? 0 : result.length();
                return ToolResult.success(toolUse.id(), result);
            } catch (Exception e) {
                Log.tool("tool error: " + toolUse.name() + " — " + e.getMessage());
                event.outcome = "error";
                event.errorType = e.getClass().getSimpleName();
                return ToolResult.error(toolUse.id(), e.getMessage());
            }
        } finally {
            if (event.shouldCommit()) {
                event.commit();
            }
        }
    }

    /// Asks the user and returns the denial message to send back to the LLM, or `null` when
    /// execution may proceed. An `always` or `never` answer is persisted for this agent, so
    /// the question is asked once per tool rather than once per call.
    String confirm(ToolUse toolUse) {
        var answer = Console.prompt("Allow " + toolUse.name() + " with " + toolUse.input() + "? (yes/always/no/never): ");
        if ("always".equalsIgnoreCase(answer) || "a".equalsIgnoreCase(answer)) {
            ToolPermission.ALLOW.store(this.agentName, toolUse.name());
            return null;
        }
        if ("never".equalsIgnoreCase(answer)) {
            ToolPermission.DENY.store(this.agentName, toolUse.name());
            return "Denied: user rejected tool execution (persisted)";
        }
        if ("yes".equalsIgnoreCase(answer) || "y".equalsIgnoreCase(answer)) {
            return null;
        }
        Log.tool("tool rejected by user: " + toolUse.name());
        return "Denied: user rejected tool execution";
    }
}
