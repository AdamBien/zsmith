import java.nio.file.Files;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.configuration.control.ZCfg;

/// Traces agent spec R1.1, R1.2, R1.3, R6.1, R7.1 — see src/main/java/airhacks/zsmith/agent/package-info.java

long pid = ProcessHandle.current().pid();

void main() throws Exception {
    System.setProperty("agent.name", "creation-default-" + this.pid);
    System.setProperty("agent.system.prompt", "configured default prompt");
    System.setProperty("agent.max.iterations", "7");
    System.setProperty("agent.temperature", "0.42");

    createResolvesProvidedValuesAndDefaults();
    persistedSystemPromptWins();
    namedAgentConfigLoaded();
    clearMemoryDiscardsHistory();
    versionResolved();
}

// R1.1 — When an agent is created, the BC shall resolve name, system prompt, iteration limit,
// and temperature from the provided values, falling back to configuration defaults.
void createResolvesProvidedValuesAndDefaults() {
    var providedName = "creation-provided-" + this.pid;
    var provided = new Agent(providedName, "provided prompt");
    if (!providedName.equals(provided.name()))
        throw new AssertionError("R1.1 — expected provided name '%s' but got: %s".formatted(providedName, provided.name()));
    if (!"provided prompt".equals(provided.systemPrompt()))
        throw new AssertionError("R1.1 — expected provided prompt but got: " + provided.systemPrompt());

    var fallback = new Agent();
    if (!("creation-default-" + this.pid).equals(fallback.name()))
        throw new AssertionError("R1.1 — expected configured default name but got: " + fallback.name());
    if (!"configured default prompt".equals(fallback.systemPrompt()))
        throw new AssertionError("R1.1 — expected configured default prompt but got: " + fallback.systemPrompt());
    if (fallback.maxIterations() != 7)
        throw new AssertionError("R1.1 — expected configured max iterations 7 but got: " + fallback.maxIterations());
    if (fallback.temperature() != 0.42f)
        throw new AssertionError("R1.1 — expected configured temperature 0.42 but got: " + fallback.temperature());
}

// R1.2 — Where a persisted system prompt exists for the agent name, the BC shall prefer it
// over the provided prompt.
void persistedSystemPromptWins() throws Exception {
    var agentName = "creation-prompt-" + this.pid;
    var agentDir = java.nio.file.Path.of(System.getProperty("user.home"), ".zsmith", agentName);
    Files.createDirectories(agentDir);
    var promptFile = agentDir.resolve("system.prompt");
    Files.writeString(promptFile, "persisted prompt R1.2");
    try {
        var agent = new Agent(agentName, "fallback prompt");
        if (!"persisted prompt R1.2".equals(agent.systemPrompt()))
            throw new AssertionError("R1.2 — expected persisted prompt to win but got: " + agent.systemPrompt());
    } finally {
        Files.deleteIfExists(promptFile);
        Files.deleteIfExists(agentDir);
    }
}

// R1.3 — When an agent is created with a name, the BC shall load that agent's named configuration.
void namedAgentConfigLoaded() throws Exception {
    var agentName = "creation-config-" + this.pid;
    var agentDir = java.nio.file.Path.of(System.getProperty("user.home"), ".zsmith", agentName);
    Files.createDirectories(agentDir);
    var configFile = agentDir.resolve("app.properties");
    Files.writeString(configFile, "creation.test.marker=loaded-" + this.pid);
    try {
        new Agent(agentName);
        var marker = ZCfg.string("creation.test.marker");
        if (!("loaded-" + this.pid).equals(marker))
            throw new AssertionError("R1.3 — expected named agent config to be loaded but marker was: " + marker);
    } finally {
        Files.deleteIfExists(configFile);
        Files.deleteIfExists(agentDir);
    }
}

// R6.1 — When memory is cleared, the BC shall discard the conversation history.
void clearMemoryDiscardsHistory() {
    var agent = new Agent("creation-memory-" + this.pid, "prompt");
    agent.memory().addUserMessage("to be discarded");
    agent.memory().addAssistantMessage("also discarded");
    if (agent.memory().size() != 2)
        throw new AssertionError("R6.1 — test setup expected 2 messages but got: " + agent.memory().size());
    agent.clearMemory();
    if (agent.memory().size() != 0)
        throw new AssertionError("R6.1 — expected empty memory after clear but size=" + agent.memory().size());
}

// R7.1 — The BC shall report the framework version resolved from the packaged manifest or version file.
void versionResolved() {
    var version = Agent.version;
    if (version == null || version.isBlank())
        throw new AssertionError("R7.1 — expected a resolved framework version but got: " + version);
}
