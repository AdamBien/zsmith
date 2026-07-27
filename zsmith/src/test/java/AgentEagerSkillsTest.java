import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.skills.boundary.SkillStore;

void main() throws IOException {
    var tempDir = Files.createTempDirectory("zunit-eagerskills");
    try {
        var skillDir = tempDir.resolve("java-conventions");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
        ---
        name: java-conventions
        description: test conventions
        ---
        use records by default
        """);
        var store = new SkillStore(List.of(tempDir));

        var eager = new Agent("eager", "You review code.").withEagerSkills(store);
        assert eager.systemPrompt().contains("## Skill: java-conventions")
                : "eager prompt should contain the skill section header but was: " + eager.systemPrompt();
        assert eager.systemPrompt().contains("use records by default")
                : "eager prompt should contain the skill content but was: " + eager.systemPrompt();
        assert !eager.systemPrompt().contains("description: test conventions")
                : "eager prompt should not contain frontmatter: " + eager.systemPrompt();
        assert !eager.tools().containsKey("load_skill")
                : "eager skills must not register the load_skill tool";

        var lazy = new Agent("lazy", "You review code.").withSkills(store);
        assert lazy.tools().containsKey("load_skill")
                : "catalog-based skills must register the load_skill tool";
        assert !lazy.systemPrompt().contains("use records by default")
                : "catalog-based prompt must not inline skill content: " + lazy.systemPrompt();

        var unchanged = new Agent("empty", "You review code.")
                .withEagerSkills(new SkillStore(List.of(tempDir.resolve("missing"))));
        assert "You review code.".equals(unchanged.systemPrompt())
                : "empty store must leave the system prompt unchanged: " + unchanged.systemPrompt();
    } finally {
        deleteRecursively(tempDir);
    }
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
