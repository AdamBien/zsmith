import java.nio.file.Files;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.episodicmemory.boundary.EpisodicMemoryStore;
import airhacks.zsmith.episodicmemory.entity.Episode;
import airhacks.zsmith.episodicmemory.entity.MemoryType;

/// Traces agent spec R4.3 and the episodic-memory half of R4.4 —
/// see src/main/java/airhacks/zsmith/agent/package-info.java

void main() throws Exception {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());

    populatedStoreInjectsCatalog();
    emptyStoreLeavesPromptUnchanged();
    appendOrderPreservesPrefix();
}

void populatedStoreInjectsCatalog() throws Exception {
    var tempFile = Files.createTempFile("agent-injection", ".json");
    Files.deleteIfExists(tempFile);
    var store = new EpisodicMemoryStore(tempFile);
    store.store(Episode.of("alpha fact", MemoryType.user));
    store.store(Episode.of("beta fact", MemoryType.project));
    store.store(Episode.of("gamma fact", MemoryType.feedback));

    var baseline = "BASE_PROMPT_MARKER";
    var agent = new Agent("memory-injection-test").withSystemPrompt(baseline).withEpisodicMemory(store);

    var prompt = agent.systemPrompt();
    assert prompt.startsWith(baseline) : "R4.3 — prompt should begin with the baseline, got: " + prompt;
    assert prompt.contains("## Recalled Memories") : "R4.3 — prompt should contain memory header, got: " + prompt;
    assert prompt.contains("alpha fact") : "R4.3 — prompt should contain alpha fact";
    assert prompt.contains("beta fact") : "R4.3 — prompt should contain beta fact";
    assert prompt.contains("gamma fact") : "R4.3 — prompt should contain gamma fact";
    assert agent.tools().containsKey("store_memory") : "R4.3 — store_memory tool should be registered, got: " + agent.tools().keySet();
    assert agent.tools().containsKey("recall_memory") : "R4.3 — recall_memory tool should be registered, got: " + agent.tools().keySet();

    store.clear();
}

void emptyStoreLeavesPromptUnchanged() throws Exception {
    var tempFile = Files.createTempFile("agent-injection-empty", ".json");
    Files.deleteIfExists(tempFile);
    var store = new EpisodicMemoryStore(tempFile);

    var baseline = "BASE_PROMPT_EMPTY";
    var agent = new Agent("memory-injection-empty-test").withSystemPrompt(baseline).withEpisodicMemory(store);

    assert baseline.equals(agent.systemPrompt()) : "R4.4 — empty store must not modify prompt, got: " + agent.systemPrompt();
}

void appendOrderPreservesPrefix() throws Exception {
    var tempFile = Files.createTempFile("agent-injection-order", ".json");
    Files.deleteIfExists(tempFile);
    var store = new EpisodicMemoryStore(tempFile);
    store.store(Episode.of("ordered fact", MemoryType.reference));

    var baseline = "BASE_PROMPT_ORDER";
    var agent = new Agent("memory-injection-order-test").withSystemPrompt(baseline).withEpisodicMemory(store);

    var prompt = agent.systemPrompt();
    var memoryIndex = prompt.indexOf("## Recalled Memories");
    assert memoryIndex > 0 : "R4.3 — memory block should appear after baseline";
    assert prompt.substring(0, memoryIndex).contains(baseline) : "R4.3 — baseline must precede memory block";
    assert prompt.contains("ordered fact") : "R4.3 — prompt should contain stored fact";

    store.clear();
}
