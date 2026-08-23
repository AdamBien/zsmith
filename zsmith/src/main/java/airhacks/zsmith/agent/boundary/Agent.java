package airhacks.zsmith.agent.boundary;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import airhacks.zsmith.json.JSONArray;

import airhacks.zsmith.agent.control.Version;
import airhacks.zsmith.agent.entity.AgentDefaults;
import airhacks.zsmith.agent.entity.AgentTurnEvent;
import airhacks.zsmith.llm.control.LLM;
import airhacks.zsmith.llm.entity.ToolChoice;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.correlation.control.Correlations;
import airhacks.zsmith.correlation.entity.Correlation;
import airhacks.zsmith.episodicmemory.boundary.EpisodicMemoryStore;
import airhacks.zsmith.episodicmemory.control.RecallMemoryTool;
import airhacks.zsmith.episodicmemory.control.StoreMemoryTool;
import airhacks.zsmith.improvements.boundary.ImprovementLog;
import airhacks.zsmith.improvements.control.ReportImprovementTool;
import airhacks.zsmith.errors.control.Errors;
import airhacks.zsmith.agentcore.boundary.AgentCoreServer;
import airhacks.zsmith.http.boundary.AgentHttpServer;
import airhacks.zsmith.http.boundary.ChatEngine;
import airhacks.zsmith.logging.control.Log;
import airhacks.zsmith.logging.control.ProgressBar;
import airhacks.zsmith.telemetry.boundary.RunTally;
import airhacks.zsmith.memory.entity.Memory;
import airhacks.zsmith.memory.entity.Message;
import airhacks.zsmith.skills.boundary.SkillStore;
import airhacks.zsmith.skills.control.LoadSkillTool;
import airhacks.zsmith.subagent.control.SubAgentTool;
import airhacks.zsmith.systemprompt.control.SystemPromptLoader;
import airhacks.zsmith.tools.boundary.SandboxTools;
import airhacks.zsmith.tools.boundary.SandboxedFileSystem;
import airhacks.zsmith.tools.boundary.ToolProfiles;
import airhacks.zsmith.tools.control.Console;
import airhacks.zsmith.tools.control.LaunchAppTool;
import airhacks.zsmith.tools.boundary.Tool;
import airhacks.zsmith.tools.control.ToolPermission;
import airhacks.zsmith.tools.entity.ToolInvocationEvent;
import airhacks.zsmith.tools.entity.ToolResult;
import airhacks.zsmith.tools.entity.ToolUse;
import airhacks.zsmith.transcripts.boundary.TranscriptLog;
import airhacks.zsmith.transcripts.entity.Transcript;

public record Agent(String name, String systemPrompt, Memory memory, Map<String, Tool> tools, int maxIterations,
        float temperature, EpisodicMemoryStore episodicMemory) {

    public Agent {
        tools = new HashMap<>(tools);
    }

    public static final String version = Version.current();

    static {
        Log.agent("zsmith v" + version);
        ZCfg.loadBaseConfig("zsmith");
    }

    public Agent(String name, String systemPrompt) {
        this(name, systemPrompt, AgentDefaults.fromConfig());
    }

    private Agent(String name, String systemPrompt, AgentDefaults defaults) {
        this(
                name != null ? name : defaults.name(),
                resolveSystemPrompt(name != null ? name : defaults.name(), systemPrompt, defaults),
                new Memory(),
                new HashMap<>(),
                defaults.maxIterations(),
                defaults.temperature(),
                null);
        ZCfg.loadNamedAgentConfig(this.name);
    }

    static String resolveSystemPrompt(String agentName, String fallback, AgentDefaults defaults) {
        var prompt = SystemPromptLoader.load(ZCfg.APP_NAME, agentName);
        if (prompt != null)
            return prompt;
        return fallback != null ? fallback : defaults.systemPrompt();
    }

    public Agent(String name) {
        this(name, null);
    }

    public Agent() {
        this(null, null);
    }

    public Agent withTool(Tool tool) {
        this.tools.put(tool.toolName(), tool);
        return this;
    }

    public Agent withTools(Tool... tools) {
        for (var tool : tools) {
            this.tools.put(tool.toolName(), tool);
        }
        return this;
    }

    public Agent withTools(List<? extends Tool> tools) {
        tools.forEach(this::withTool);
        return this;
    }

    public Agent withUserIOTools() {
        return withTools(ToolProfiles.userIO());
    }

    public Agent withFileIOTools() {
        return withTools(ToolProfiles.fileIO(this.name));
    }

    public Agent withSandbox(Path rootDirectory, SandboxTools... tools) {
        var sandbox = new SandboxedFileSystem(rootDirectory);
        var selected = tools.length == 0 ? SandboxTools.values() : tools;
        for (var tool : selected) {
            withTool(tool.create(sandbox));
        }
        return this;
    }

    public Agent withAllTools() {
        return withTools(ToolProfiles.all());
    }

    public Agent withLaunchAppTool(String toolName, String description, String command) {
        return withTool(LaunchAppTool.create(toolName, description, command));
    }

    public Agent withLaunchAppTool() {
        return withTool(LaunchAppTool.fromConfig());
    }

    public Agent withSystemPrompt(String systemPrompt) {
        return new Agent(this.name, systemPrompt, this.memory, this.tools, this.maxIterations, this.temperature,
                this.episodicMemory);
    }

    public Agent withMaxIterations(int maxIterations) {
        return new Agent(this.name, this.systemPrompt, this.memory, this.tools, maxIterations, this.temperature,
                this.episodicMemory);
    }

    public Agent withTemperature(float temperature) {
        return new Agent(this.name, this.systemPrompt, this.memory, this.tools, this.maxIterations, temperature,
                this.episodicMemory);
    }

    public Agent withEpisodicMemory() {
        return withEpisodicMemory(EpisodicMemoryStore.forAgent(this.name));
    }

    public Agent withSharedEpisodicMemory() {
        return withEpisodicMemory(new EpisodicMemoryStore());
    }

    public Agent withEpisodicMemory(EpisodicMemoryStore store) {
        var catalog = store.catalog();
        var enrichedPrompt = catalog.isEmpty()
                ? this.systemPrompt
                : this.systemPrompt + "\n\n" + catalog;
        var agent = new Agent(this.name, enrichedPrompt, this.memory, this.tools, this.maxIterations,
                this.temperature, store);
        agent.tools.put("store_memory", new StoreMemoryTool(store));
        agent.tools.put("recall_memory", new RecallMemoryTool(store));
        return agent;
    }

    /// Opt-in: the tool definition ships with every request, so it is worth its cost
    /// only while an agent's instructions are still being worked on.
    public Agent withImprovementLog() {
        return withImprovementLog(ImprovementLog.forAgent(this.name));
    }

    public Agent withImprovementLog(ImprovementLog log) {
        var agent = new Agent(this.name, this.systemPrompt, this.memory, this.tools, this.maxIterations,
                this.temperature, this.episodicMemory);
        agent.tools.put("report_improvement", new ReportImprovementTool(log));
        return agent;
    }

    public Agent withSubAgent(Agent childAgent) {
        return withTool(new SubAgentTool(childAgent));
    }

    public Agent withSequentialSubAgent(Agent childAgent) {
        return withTool(new SubAgentTool(childAgent, false));
    }

    public Agent withHttpServer(int port) {
        AgentHttpServer.start(sessionEngine(), port);
        return this;
    }

    public Agent withAgentCoreServer(int port) {
        AgentCoreServer.start(sessionEngine(), port);
        return this;
    }

    ChatEngine sessionEngine() {
        var perSession = new ConcurrentHashMap<String, Agent>();
        return (sessionId, message) -> perSession
                .computeIfAbsent(sessionId, id -> cloneForSession())
                .chat(message);
    }

    Agent cloneForSession() {
        return new Agent(this.name, this.systemPrompt, new Memory(), this.tools,
                this.maxIterations, this.temperature, this.episodicMemory);
    }

    public Agent withSkills() {
        return withSkills(SkillStore.forAgent(this.name));
    }

    public Agent withSkillsFromDirectory(String path) {
        return withSkills(new SkillStore(List.of(Path.of(path))));
    }

    public Agent withSkillsNamed(String... names) {
        return withSkills(SkillStore.forAgent(this.name).filtered(Set.of(names)));
    }

    public Agent withSkills(SkillStore store) {
        var catalog = store.catalog();
        var enrichedPrompt = catalog.isEmpty()
                ? this.systemPrompt
                : this.systemPrompt + "\n\n" + catalog;
        var agent = new Agent(this.name, enrichedPrompt, this.memory, this.tools, this.maxIterations, this.temperature,
                this.episodicMemory);
        agent.tools.put("load_skill", new LoadSkillTool(store));
        return agent;
    }

    public Agent withEagerSkills(String... names) {
        return withEagerSkills(SkillStore.forAgent(this.name).filtered(Set.of(names)));
    }

    public Agent withEagerSkills(SkillStore store) {
        var inlined = store.inlinedSkills();
        if (inlined.isEmpty()) {
            return this;
        }
        return new Agent(this.name, this.systemPrompt + "\n\n" + inlined, this.memory, this.tools,
                this.maxIterations, this.temperature, this.episodicMemory);
    }

    /// Sorted by name so equal tool sets serialize byte-identically: the definitions render
    /// at the very front of the cached prompt prefix, where a `HashMap`'s incidental order
    /// would otherwise decide whether parallel sessions of the same agent share a cache entry.
    JSONArray toolDefinitions() {
        var array = new JSONArray();
        this.tools.values().stream()
                .sorted(Comparator.comparing(Tool::toolName))
                .map(Tool::toToolDefinition)
                .forEach(array::put);
        return array;
    }

    /// Takes the correlation as an argument rather than reading it from the ambient scope:
    /// parallel-capable tools are submitted to a virtual-thread executor, which does not
    /// inherit scoped value bindings. It is re-bound around the tool body so that whatever
    /// the tool reaches — episodic memory, a nested agent, another LLM call — records the
    /// same run, on whichever thread it ends up.
    ToolResult executeTool(ToolUse toolUse, Correlation correlation) {
        var event = new ToolInvocationEvent();
        event.agentName = this.name;
        event.runId = correlation.runId();
        event.iteration = correlation.iteration();
        event.toolUseId = toolUse.id();
        event.toolName = toolUse.name();
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
                var answer = Console.prompt("Allow " + toolUse.name() + " with " + toolUse.input() + "? (yes/always/no/never): ");
                if ("always".equalsIgnoreCase(answer) || "a".equalsIgnoreCase(answer)) {
                    ZCfg.storeAgentProperty(this.name, ToolPermission.PREFIX + toolUse.name(), "allow");
                    Log.tool("tool permission persisted: " + toolUse.name() + " = allow");
                } else if ("never".equalsIgnoreCase(answer)) {
                    ZCfg.storeAgentProperty(this.name, ToolPermission.PREFIX + toolUse.name(), "deny");
                    Log.tool("tool permission persisted: " + toolUse.name() + " = deny");
                    event.outcome = "denied";
                    return ToolResult.error(toolUse.id(), "Denied: user rejected tool execution (persisted)");
                } else if (!"yes".equalsIgnoreCase(answer) && !"y".equalsIgnoreCase(answer)) {
                    Log.tool("tool rejected by user: " + toolUse.name());
                    event.outcome = "denied";
                    return ToolResult.error(toolUse.id(), "Denied: user rejected tool execution");
                }
            }
            try {
                Log.tool("→ %s %s".formatted(toolUse.name(), truncate(String.valueOf(toolUse.input()), 200)));
                var start = System.currentTimeMillis();
                var result = ScopedValue.where(Correlations.CURRENT, correlation)
                        .call(() -> tool.execute(toolUse.input()));
                var duration = System.currentTimeMillis() - start;
                Log.tool("← %s %s".formatted(toolUse.name(), result == null ? "<null>" : truncate(result, 200)));
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

    public String act() {
        return chat("go");
    }

    /// The model name reported by the most recent LLM response — the model actually served,
    /// which can differ from the configured one (529 fallback, lightmetal's own config).
    /// `"unknown"` before the first chat / act invocation.
    public String modelName() {
        return LLM.servedModelName();
    }

    public String chat(String userMessage) {
        Objects.requireNonNull(userMessage, "Chat requires a message, use act() for agentic workflows");
        Log.prompt(userMessage);
        this.memory.addUserMessage(userMessage);

        var progress = new ProgressBar(this.maxIterations);
        try {
            return chatLoop(progress);
        } catch (RuntimeException e) {
            var summary = Errors.summarize(e);
            Log.error(summary);
            return summary;
        }
    }

    String chatLoop(ProgressBar progress) {
        var toolCounts = new HashMap<String, Integer>();
        /// Blank and depth 0 for a top-level chat; the delegating run and its depth when a
        /// sub-agent is running, which is what links a child run back into the tree.
        var parent = Correlations.current();
        var runId = UUID.randomUUID().toString();
        String lastText = null;
        String exitReason = "max_iterations";
        int turns = 0;
        try {
        for (int iteration = 0; iteration < this.maxIterations; iteration++) {
            turns = iteration + 1;
            var correlation = new Correlation(runId, iteration, parent.depth());
            var turnEvent = new AgentTurnEvent();
            turnEvent.agentName = this.name;
            turnEvent.runId = runId;
            turnEvent.parentRunId = parent.runId();
            turnEvent.depth = parent.depth();
            turnEvent.iteration = iteration;
            turnEvent.begin();
            try {
                var toolChoice = ToolChoice.forTurn(iteration);
                var response = ScopedValue.where(Correlations.CURRENT, correlation)
                        .call(() -> LLM.invoke(
                                this.systemPrompt,
                                this.memory.toJSON(),
                                toolDefinitions(),
                                this.temperature,
                                toolChoice));
                progress.addLLMInvocation();
                progress.update(iteration + 1, RunTally.runningTokens(runId));

                var content = response.getJSONArray("content");
                var stopReason = response.optString("stop_reason", "end_turn");
                turnEvent.stopReason = stopReason;

                var textParts = extractTextContent(content);
                var toolUses = extractToolUses(content);
                turnEvent.toolUseCount = toolUses.size();
                if (!textParts.isEmpty()) {
                    lastText = String.join("\n", textParts);
                }
                toolUses.forEach(tu -> toolCounts.merge(tu.name(), 1, Integer::sum));

                if (toolUses.isEmpty() || !"tool_use".equals(stopReason)) {
                    turnEvent.terminal = true;
                    exitReason = stopReason;
                    if (!textParts.isEmpty()) {
                        var assistantResponse = String.join("\n", textParts);
                        this.memory.addAssistantMessage(assistantResponse);
                        Log.answer(assistantResponse);
                        return assistantResponse;
                    }
                    return "";
                }

                addAssistantContentToMemory(content);

                var toolResults = new JSONArray();
                var parallelTools = toolUses.stream()
                        .filter(tu -> {
                            var tool = this.tools.get(tu.name());
                            return tool != null && tool.parallel();
                        })
                        .toList();
                var sequentialTools = toolUses.stream()
                        .filter(tu -> !parallelTools.contains(tu))
                        .toList();
                turnEvent.parallelToolCount = parallelTools.size();
                turnEvent.sequentialToolCount = sequentialTools.size();

                if (!parallelTools.isEmpty()) {
                    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        var futures = parallelTools.stream()
                                .map(tu -> Map.entry(tu, executor.submit(() -> executeTool(tu, correlation))))
                                .toList();
                        for (var entry : futures) {
                            try {
                                toolResults.put(entry.getValue().get().toContentBlock());
                            } catch (Exception e) {
                                toolResults.put(ToolResult.error(entry.getKey().id(), e.getMessage()).toContentBlock());
                            }
                        }
                    }
                }
                for (var toolUse : sequentialTools) {
                    var result = executeTool(toolUse, correlation);
                    toolResults.put(result.toContentBlock());
                }
                progress.addToolInvocations(toolUses.size());
                var message = Message.withContentBlocks("user", toolResults);
                this.memory.addMessage(message);
            } finally {
                if (turnEvent.shouldCommit()) {
                    turnEvent.commit();
                }
            }
        }

        Log.warning("max iterations reached (" + this.maxIterations + ")");
        return "Max iterations reached";
        } finally {
            Log.agent("loop end (%s) memory=%d messages tool_counts=%s"
                    .formatted(exitReason, this.memory.size(), toolCounts));
            if (lastText != null) {
                Log.agent("last assistant text: " + truncate(lastText, 500));
            }
            storeTranscript(runId, exitReason, turns);
            progress.summary(RunTally.runningTokens(runId));
            RunTally.discard(runId);
        }
    }

    /// The payload half of the claim check: the events of this run carry `runId`, this stores
    /// what was actually said under the same key. Off unless `transcripts.enabled` says
    /// otherwise — writing every conversation to disk is the user's decision.
    void storeTranscript(String runId, String exitReason, int turns) {
        if (!TranscriptLog.enabled()) {
            return;
        }
        TranscriptLog.forAgent(this.name)
                .save(Transcript.of(runId, this.name, exitReason, turns, this.memory.toJSON().toString()));
    }

    static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "… (+%d chars)".formatted(text.length() - max);
    }

    public void clearMemory() {
        this.memory.clear();
    }

    List<String> extractTextContent(JSONArray content) {
        var texts = new ArrayList<String>();
        for (int i = 0; i < content.length(); i++) {
            var block = content.getJSONObject(i);
            if ("text".equals(block.optString("type"))) {
                texts.add(block.getString("text"));
            }
        }
        return texts;
    }

    List<ToolUse> extractToolUses(JSONArray content) {
        var toolUses = new ArrayList<ToolUse>();
        for (int i = 0; i < content.length(); i++) {
            var block = content.getJSONObject(i);
            if (ToolUse.isToolUse(block)) {
                toolUses.add(ToolUse.fromJSON(block));
            }
        }
        return toolUses;
    }

    void addAssistantContentToMemory(JSONArray content) {
        this.memory.addMessage(Message.withContentBlocks("assistant", content));
    }
}
