import java.nio.file.Files;
import java.nio.file.Path;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.episodicmemory.boundary.EpisodicMemoryStore;
import airhacks.zsmith.episodicmemory.entity.Episode;
import airhacks.zsmith.episodicmemory.entity.MemoryType;

void main() throws Exception {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());

    storesAndQueries();
    reloadsFromDisk();
    migratesLegacyJson();
    dedupesIdenticalMemories();
    sharesUserMemoriesAcrossAgents();
}

void storesAndQueries() throws Exception {
    var databaseRoot = Files.createTempDirectory("episodic-test");
    var store = new EpisodicMemoryStore(databaseRoot);

    // store episodes of different types
    store.store(Episode.of("user pref", MemoryType.user));
    store.store(Episode.of("project note", MemoryType.project));
    store.store(Episode.of("another user pref", MemoryType.user));
    store.store(Episode.of("feedback item", MemoryType.feedback));

    // allEpisodes returns all
    assert store.allEpisodes().size() == 4 : "expected 4 episodes, got: " + store.allEpisodes().size();

    // byType filters correctly
    var userEpisodes = store.byType(MemoryType.user);
    assert userEpisodes.size() == 2 : "expected 2 user episodes, got: " + userEpisodes.size();
    assert userEpisodes.stream().allMatch(e -> e.type() == MemoryType.user) : "all should be user type";

    var projectEpisodes = store.byType(MemoryType.project);
    assert projectEpisodes.size() == 1 : "expected 1 project episode, got: " + projectEpisodes.size();

    // recent(n) returns last n
    var recent2 = store.recent(2);
    assert recent2.size() == 2 : "expected 2 recent, got: " + recent2.size();
    assert "feedback item".equals(recent2.getLast().content()) : "last recent should be feedback item";

    // recent(0) returns empty
    assert store.recent(0).isEmpty() : "recent(0) should be empty";

    // catalog formats stored episodes
    var catalog = store.catalog();
    assert catalog.contains("## Recalled Memories") : "catalog should contain header, got: " + catalog;
    assert catalog.contains("user pref") : "catalog should contain user pref";
    assert catalog.contains("project note") : "catalog should contain project note";
    assert catalog.contains("feedback item") : "catalog should contain feedback item";
    for (var line : catalog.lines().toList()) {
        if (line.startsWith("- ")) {
            assert line.matches("^- \\[\\d{4}-\\d{2}-\\d{2} \\w+\\] .+") : "bullet line malformed: " + line;
        }
    }

    // per-type cap respected; total cap respected
    for (int i = 0; i < 30; i++) {
        store.store(Episode.of("bulk " + i, MemoryType.feedback));
    }
    var capped = store.catalog(5, 20);
    var feedbackLines = capped.lines().filter(l -> l.contains("] bulk ")).count();
    assert feedbackLines <= 5 : "expected ≤ 5 bulk feedback lines, got: " + feedbackLines;
    var bulletCount = capped.lines().filter(l -> l.startsWith("- ")).count();
    assert bulletCount <= 20 : "expected ≤ 20 total bullets, got: " + bulletCount;

    // zeroed caps disable injection
    assert "".equals(store.catalog(0, 20)) : "catalog(0, 20) should be empty";
    assert "".equals(store.catalog(5, 0)) : "catalog(5, 0) should be empty";

    // clear removes all; catalog becomes empty
    store.clear();
    assert store.allEpisodes().isEmpty() : "should be empty after clear";
    assert !Files.exists(databaseRoot.resolve("episodes")) : "episodes table should be deleted after clear";
    assert "".equals(store.catalog()) : "catalog of empty store should be empty";
}

/// Each memory is its own page, so a second store over the same folder sees what
/// the first one wrote — including multi-line content stored within the same second.
void reloadsFromDisk() throws Exception {
    var databaseRoot = Files.createTempDirectory("episodic-reload");
    var store = new EpisodicMemoryStore(databaseRoot);
    store.store(Episode.of("first fact", MemoryType.user));
    store.store(Episode.of("second\nfact with <markup> & entities", MemoryType.project));

    var reloaded = new EpisodicMemoryStore(databaseRoot);
    assert reloaded.allEpisodes().size() == 2 : "expected 2 reloaded episodes, got: " + reloaded.allEpisodes().size();
    var project = reloaded.byType(MemoryType.project);
    assert project.size() == 1 : "expected 1 reloaded project episode, got: " + project.size();
    assert "second\nfact with <markup> & entities".equals(project.getFirst().content())
            : "content should survive the round-trip, got: " + project.getFirst().content();
}

void migratesLegacyJson() throws Exception {
    var databaseRoot = Files.createTempDirectory("episodic-migration");
    var legacy = databaseRoot.resolve("episodic-memory.json");
    Files.writeString(legacy, """
            [
              {"content":"legacy fact","timestamp":"2026-01-02T03:04:05Z","type":"user"},
              {"content":"legacy note","timestamp":"2026-01-02T03:04:06Z","category":"project"}
            ]
            """);

    var store = new EpisodicMemoryStore(databaseRoot);

    assert store.allEpisodes().size() == 2 : "expected 2 migrated episodes, got: " + store.allEpisodes().size();
    assert "legacy fact".equals(store.allEpisodes().getFirst().content()) : "migrated content mismatch";
    assert store.byType(MemoryType.project).size() == 1 : "legacy 'category' should map to the type";
    assert !Files.exists(legacy) : "migrated file should be moved aside";
    assert Files.exists(databaseRoot.resolve("episodic-memory.json.migrated")) : "migrated file should be kept";
    assert new EpisodicMemoryStore(databaseRoot).allEpisodes().size() == 2 : "migration must not run twice";
}

void dedupesIdenticalMemories() throws Exception {
    var databaseRoot = Files.createTempDirectory("episodic-dedup");
    var store = new EpisodicMemoryStore(databaseRoot);

    assert store.store(Episode.of("User's name is Duke", MemoryType.user)) : "first store should report stored";
    assert !store.store(Episode.of("User's name is Duke", MemoryType.user)) : "repeat should report nothing stored";
    assert store.store(Episode.of("User's name is Duke", MemoryType.project)) : "same text, other type is a new memory";

    assert store.allEpisodes().size() == 2 : "expected 2 memories, got: " + store.allEpisodes().size();
    assert new EpisodicMemoryStore(databaseRoot).allEpisodes().size() == 2 : "duplicate must not reach disk";
}

/// A memory about the user is written to the shared database, so a second agent —
/// or a subagent — knows the same person without inheriting the first agent's notes.
void sharesUserMemoriesAcrossAgents() throws Exception {
    var shared = Files.createTempDirectory("episodic-shared");
    var first = new EpisodicMemoryStore(Files.createTempDirectory("episodic-agent-one"), shared);
    first.store(Episode.of("User is a Java architect", MemoryType.user));
    first.store(Episode.of("Episode 148 needs a transcript", MemoryType.project));

    // an agent writing straight to the shared database must not export its notes
    new EpisodicMemoryStore(shared).store(Episode.of("shared-scope project note", MemoryType.project));

    var second = new EpisodicMemoryStore(Files.createTempDirectory("episodic-agent-two"), shared);
    assert second.byType(MemoryType.user).size() == 1 : "user memory should cross agents, got: " + second.byType(MemoryType.user);
    assert second.byType(MemoryType.project).isEmpty() : "project notes must stay agent-local, got: " + second.byType(MemoryType.project);
    assert second.allEpisodes().size() == 1 : "second agent should see only the shared memory";

    // clearing an agent leaves the shared user memory for everyone else
    first.clear();
    assert new EpisodicMemoryStore(Files.createTempDirectory("episodic-agent-three"), shared)
            .byType(MemoryType.user).size() == 1 : "clear must not wipe the shared scope";

    promotesLegacyUserMemories(shared);
}

/// An agent upgrading from the JSON format has its user memories moved to the
/// shared scope, collapsing the copies a repeating model left behind.
void promotesLegacyUserMemories(Path shared) throws Exception {
    var agentRoot = Files.createTempDirectory("episodic-legacy-agent");
    Files.writeString(agentRoot.resolve("episodic-memory.json"), """
            [
              {"content":"User's name is Duke","timestamp":"2026-01-02T03:04:05Z","type":"user"},
              {"content":"User's name is Duke","timestamp":"2026-01-02T03:04:06Z","type":"user"},
              {"content":"transcript pending","timestamp":"2026-01-02T03:04:07Z","type":"project"}
            ]
            """);

    var upgraded = new EpisodicMemoryStore(agentRoot, shared);
    assert upgraded.byType(MemoryType.user).size() == 2
            : "one Duke plus the existing shared memory, got: " + upgraded.byType(MemoryType.user);
    assert upgraded.byType(MemoryType.project).size() == 1 : "project note should stay agent-local";
    assert !Files.exists(agentRoot.resolve("episodes").resolve("2026-01-02-030405.html"))
            : "the user memory should have moved out of the agent database";
    assert Files.exists(shared.resolve("episodes").resolve("2026-01-02-030405.html"))
            : "the user memory should have landed in the shared database";
}
