import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import airhacks.zsmith.agent.boundary.Agent;

/// Traces agent spec R4.2, R4.4, R4.5 — see src/main/java/airhacks/zsmith/agent/package-info.java
/// (R4.3 and the episodic-memory half of R4.4 are traced in AgentMemoryInjectionTest.)

void main() throws Exception {
    skillsCatalogInjectedAndLoaderRegistered();
    emptySkillCatalogLeavesPromptUnchanged();
    subAgentExposedAsTool();
}

// R4.2 — When skills are attached, the BC shall append the skill catalog to the system prompt
// and register a skill-loading tool.
void skillsCatalogInjectedAndLoaderRegistered() throws IOException {
    var tempDir = Files.createTempDirectory("agent-equip-skills");
    try {
        var skillDir = tempDir.resolve("greeting-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: greeting-skill
                description: Greets politely
                ---
                Say hello.
                """);
        var agent = new Agent("equip-r42", "base prompt R4.2").withSkillsFromDirectory(tempDir.toString());
        if (!agent.systemPrompt().startsWith("base prompt R4.2"))
            throw new AssertionError("R4.2 — expected original prompt preserved but got: " + agent.systemPrompt());
        if (!agent.systemPrompt().contains("greeting-skill"))
            throw new AssertionError("R4.2 — expected skill catalog appended to system prompt but got: " + agent.systemPrompt());
        if (!agent.tools().containsKey("load_skill"))
            throw new AssertionError("R4.2 — expected load_skill tool registered but got: " + agent.tools().keySet());
    } finally {
        deleteRecursively(tempDir);
    }
}

// R4.4 — If an attached skill or memory catalog is empty, then the BC shall leave the system
// prompt unchanged. (Skill variant; the memory variant is traced in AgentMemoryInjectionTest.)
void emptySkillCatalogLeavesPromptUnchanged() throws IOException {
    var tempDir = Files.createTempDirectory("agent-equip-empty");
    try {
        var agent = new Agent("equip-r44", "unchanged prompt R4.4").withSkillsFromDirectory(tempDir.toString());
        if (!"unchanged prompt R4.4".equals(agent.systemPrompt()))
            throw new AssertionError("R4.4 — expected unchanged prompt for empty catalog but got: " + agent.systemPrompt());
    } finally {
        deleteRecursively(tempDir);
    }
}

// R4.5 — When a sub-agent is attached, the BC shall expose the child agent as a tool.
void subAgentExposedAsTool() {
    var child = new Agent("equip-child", "child prompt");
    var parent = new Agent("equip-parent", "parent prompt").withSubAgent(child);
    if (!parent.tools().containsKey("delegate_to_equip-child"))
        throw new AssertionError("R4.5 — expected delegate_to_equip-child tool but got: " + parent.tools().keySet());
}

void deleteRecursively(Path dir) throws IOException {
    try (var paths = Files.walk(dir)) {
        paths.sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                throw new IllegalStateException("cannot delete " + path, e);
            }
        });
    }
}
