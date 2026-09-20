import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.episodicmemory.boundary.EpisodicMemoryStore;
import airhacks.zsmith.episodicmemory.entity.Episode;
import airhacks.zsmith.episodicmemory.entity.MemoryType;

/// Traces episodicmemory spec R1.1–R1.5, R2.1–R2.3, R3.1, R5.1–R5.4, R6.1–R6.5,
/// R7.1–R7.2 and R8.1–R8.5 — see
/// src/main/java/airhacks/zsmith/episodicmemory/package-info.java

void main() throws Exception {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());

    storesAndQueries();
    recallsEverythingWhenFewerAreKept();
    rejectsAFactWithoutAStatement();
    shortensALongMemoryInTheCatalog();
    reloadsFromDisk();
    migratesLegacyJson();
    dedupesIdenticalMemories();
    sharesUserMemoriesAcrossAgents();
    skipsUnreadableMemories();
    survivesAnUnreadableStore();
}

// R1.1 — When a fact and its type are supplied, the BC shall keep them durably and
// report the fact as newly remembered.
// R3.1 — When a type is supplied, the BC shall return every memory carrying it,
// oldest first.
// R2.1 — When a count is requested, the BC shall return at most that many most
// recently kept memories, oldest first.
// R2.2 — If the requested count is zero or negative, then the BC shall return no
// memory.
// R6.1 — The BC shall publish kept memories as a catalog carrying each memory's date
// and type, oldest first.
// R6.2 — The BC shall cap the catalog at a configured count per type and a configured
// total.
// R6.4 — If no memory is kept, or either cap is zero or negative, then the BC shall
// publish nothing.
// R7.1 — When forgetting is requested, the BC shall drop the memories this agent
// learned, from the run and from its database.
void storesAndQueries() throws Exception {
    var databaseRoot = Files.createTempDirectory("episodic-test");
    var store = new EpisodicMemoryStore(databaseRoot);

    // store episodes of different types
    assert store.store(Episode.of("user pref", MemoryType.user)) : "R1.1 — a new fact reports as remembered";
    store.store(Episode.of("project note", MemoryType.project));
    store.store(Episode.of("another user pref", MemoryType.user));
    store.store(Episode.of("feedback item", MemoryType.feedback));

    // allEpisodes returns all
    assert store.allEpisodes().size() == 4 : "R1.1 — expected 4 episodes, got: " + store.allEpisodes().size();

    // byType filters correctly
    var userEpisodes = store.byType(MemoryType.user);
    assert userEpisodes.size() == 2 : "R3.1 — expected 2 user episodes, got: " + userEpisodes.size();
    assert userEpisodes.stream().allMatch(e -> e.type() == MemoryType.user) : "R3.1 — all should be user type";
    assert "user pref".equals(userEpisodes.getFirst().content()) : "R3.1 — oldest first, got: " + userEpisodes;

    var projectEpisodes = store.byType(MemoryType.project);
    assert projectEpisodes.size() == 1 : "R3.1 — expected 1 project episode, got: " + projectEpisodes.size();

    // recent(n) returns last n
    var recent2 = store.recent(2);
    assert recent2.size() == 2 : "R2.1 — expected 2 recent, got: " + recent2.size();
    assert "feedback item".equals(recent2.getLast().content()) : "R2.1 — last recent should be feedback item";

    // recent(0) returns empty
    assert store.recent(0).isEmpty() : "R2.2 — recent(0) should be empty";
    assert store.recent(-3).isEmpty() : "R2.2 — a negative count should be empty";

    // catalog formats stored episodes
    var catalog = store.catalog();
    assert catalog.contains("## Recalled Memories") : "R6.1 — catalog should contain header, got: " + catalog;
    assert catalog.contains("user pref") : "R6.1 — catalog should contain user pref";
    assert catalog.contains("project note") : "R6.1 — catalog should contain project note";
    assert catalog.contains("feedback item") : "R6.1 — catalog should contain feedback item";
    for (var line : catalog.lines().toList()) {
        if (line.startsWith("- ")) {
            assert line.matches("^- \\[\\d{4}-\\d{2}-\\d{2} \\w+\\] .+") : "R6.1 — bullet line malformed: " + line;
        }
    }
    var bullets = catalog.lines().filter(line -> line.startsWith("- ")).toList();
    assert bullets.getFirst().contains("user pref") : "R6.1 — oldest memory leads the catalog, got: " + bullets;

    // per-type cap respected; total cap respected
    for (int i = 0; i < 30; i++) {
        store.store(Episode.of("bulk " + i, MemoryType.feedback));
    }
    var capped = store.catalog(5, 20);
    var feedbackLines = capped.lines().filter(l -> l.contains("] bulk ")).count();
    assert feedbackLines <= 5 : "R6.2 — expected ≤ 5 bulk feedback lines, got: " + feedbackLines;
    var bulletCount = capped.lines().filter(l -> l.startsWith("- ")).count();
    assert bulletCount <= 20 : "R6.2 — expected ≤ 20 total bullets, got: " + bulletCount;

    // zeroed caps disable injection
    assert "".equals(store.catalog(0, 20)) : "R6.4 — catalog(0, 20) should be empty";
    assert "".equals(store.catalog(5, 0)) : "R6.4 — catalog(5, 0) should be empty";
    assert "".equals(store.catalog(-1, 20)) : "R6.4 — a negative cap should be empty";

    // clear removes all; catalog becomes empty
    store.clear();
    assert store.allEpisodes().isEmpty() : "R7.1 — should be empty after clear";
    assert !Files.exists(databaseRoot.resolve("episodes")) : "R7.1 — episodes table should be deleted after clear";
    assert "".equals(store.catalog()) : "R6.4 — catalog of empty store should be empty";
}

// R2.3 — If fewer memories are kept than requested, then the BC shall return every
// kept memory.
void recallsEverythingWhenFewerAreKept() throws Exception {
    var store = new EpisodicMemoryStore(Files.createTempDirectory("episodic-fewer"));
    store.store(Episode.of("the only fact", MemoryType.project));

    assert store.recent(100).size() == 1 : "R2.3 — a count beyond the store returns what it holds";
    assert new EpisodicMemoryStore(Files.createTempDirectory("episodic-none")).recent(10).isEmpty()
            : "R2.3 — an empty store returns nothing";
}

// R1.3 — If the supplied fact carries no statement, then the BC shall reject it.
// R1.4 — When a fact is kept without a time, the BC shall record the time it was kept.
void rejectsAFactWithoutAStatement() {
    for (var blank : new String[] { "", "   ", null }) {
        try {
            new Episode(blank, null, MemoryType.project);
            throw new AssertionError("R1.3 — a fact without a statement must be rejected: '" + blank + "'");
        } catch (IllegalArgumentException expected) {
            // the rejection R1.3 asks for
        }
    }

    var kept = Episode.of("a fact worth keeping", MemoryType.project);
    assert kept.timestamp() != null : "R1.4 — a fact kept without a time must be timed";
    assert !Instant.parse(kept.timestamp()).isAfter(Instant.now().plusSeconds(1))
            : "R1.4 — the recorded time is when it was kept, got: " + kept.timestamp();
}

// R6.3 — The BC shall shorten a memory exceeding the publishable length.
// R6.5 — The BC shall present the catalog as background context rather than as
// instruction.
void shortensALongMemoryInTheCatalog() throws Exception {
    var store = new EpisodicMemoryStore(Files.createTempDirectory("episodic-long"));
    var essay = "the user explains at length why records beat classes ".repeat(20);
    store.store(Episode.of(essay, MemoryType.feedback));

    var catalog = store.catalog();
    var bullet = catalog.lines().filter(line -> line.startsWith("- ")).findFirst().orElseThrow();

    assert bullet.length() < essay.length() : "R6.3 — a long memory must be shortened, got " + bullet.length();
    assert bullet.endsWith("...") : "R6.3 — a shortened memory must show that it was cut, got: " + bullet;
    assert catalog.contains("hints, not commands")
            : "R6.5 — the catalog must read as background, not as instruction, got: " + catalog;
}

/// Each memory is its own page, so a second store over the same folder sees what
/// the first one wrote — including multi-line content stored within the same second.
// R1.5 — The BC shall return a kept fact in a later session.
void reloadsFromDisk() throws Exception {
    var databaseRoot = Files.createTempDirectory("episodic-reload");
    var store = new EpisodicMemoryStore(databaseRoot);
    store.store(Episode.of("first fact", MemoryType.user));
    store.store(Episode.of("second\nfact with <markup> & entities", MemoryType.project));

    var reloaded = new EpisodicMemoryStore(databaseRoot);
    assert reloaded.allEpisodes().size() == 2 : "R1.5 — expected 2 reloaded episodes, got: " + reloaded.allEpisodes().size();
    var project = reloaded.byType(MemoryType.project);
    assert project.size() == 1 : "R1.5 — expected 1 reloaded project episode, got: " + project.size();
    assert "second\nfact with <markup> & entities".equals(project.getFirst().content())
            : "R1.5 — content should survive the round-trip, got: " + project.getFirst().content();
}

// R8.2 — When a store written in the superseded single-file format is opened, the BC
// shall import its memories once and set the old file aside.
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

    assert store.allEpisodes().size() == 2 : "R8.2 — expected 2 migrated episodes, got: " + store.allEpisodes().size();
    assert "legacy fact".equals(store.allEpisodes().getFirst().content()) : "R8.2 — migrated content mismatch";
    assert store.byType(MemoryType.project).size() == 1 : "R8.2 — legacy 'category' should map to the type";
    assert !Files.exists(legacy) : "R8.2 — migrated file should be moved aside";
    assert Files.exists(databaseRoot.resolve("episodic-memory.json.migrated")) : "R8.2 — migrated file should be kept";
    assert new EpisodicMemoryStore(databaseRoot).allEpisodes().size() == 2 : "R8.2 — migration must not run twice";
}

// R1.2 — If a fact carrying the same statement and type is already kept, then the BC
// shall keep the stored one unchanged and report that nothing was kept.
void dedupesIdenticalMemories() throws Exception {
    var databaseRoot = Files.createTempDirectory("episodic-dedup");
    var store = new EpisodicMemoryStore(databaseRoot);

    assert store.store(Episode.of("User's name is Duke", MemoryType.user)) : "R1.2 — first store should report stored";
    assert !store.store(Episode.of("User's name is Duke", MemoryType.user)) : "R1.2 — repeat should report nothing stored";
    assert store.store(Episode.of("User's name is Duke", MemoryType.project)) : "R1.2 — same text, other type is a new memory";

    assert store.allEpisodes().size() == 2 : "R1.2 — expected 2 memories, got: " + store.allEpisodes().size();
    assert new EpisodicMemoryStore(databaseRoot).allEpisodes().size() == 2 : "R1.2 — duplicate must not reach disk";
}

/// A memory about the user is written to the shared database, so a second agent —
/// or a subagent — knows the same person without inheriting the first agent's notes.
// R5.1 — When a memory about the user is kept by an agent-owned memory, the BC shall
// write it to the shared scope.
// R5.2 — When a memory of any other type is kept, the BC shall write it to the scope
// owned by the agent that learned it.
// R5.3 — The BC shall read the scopes it holds as one, so recall, search and the
// catalog span them.
// R5.4 — While reading a shared scope it does not own, the BC shall admit only the
// memories about the user.
// R7.2 — While what is known about the user lives in a scope this agent does not own,
// the BC shall keep it.
void sharesUserMemoriesAcrossAgents() throws Exception {
    var shared = Files.createTempDirectory("episodic-shared");
    var first = new EpisodicMemoryStore(Files.createTempDirectory("episodic-agent-one"), shared);
    first.store(Episode.of("User is a Java architect", MemoryType.user));
    first.store(Episode.of("Episode 148 needs a transcript", MemoryType.project));

    // an agent writing straight to the shared database must not export its notes
    new EpisodicMemoryStore(shared).store(Episode.of("shared-scope project note", MemoryType.project));

    var second = new EpisodicMemoryStore(Files.createTempDirectory("episodic-agent-two"), shared);
    assert second.byType(MemoryType.user).size() == 1 : "R5.1 — user memory should cross agents, got: " + second.byType(MemoryType.user);
    assert second.byType(MemoryType.project).isEmpty() : "R5.2 — project notes must stay agent-local, got: " + second.byType(MemoryType.project);
    assert second.allEpisodes().size() == 1 : "R5.4 — second agent should see only the shared memory";
    assert second.search("architect", 10).size() == 1 : "R5.3 — search spans the scopes a store reads";
    assert second.catalog().contains("Java architect") : "R5.3 — the catalog spans the scopes a store reads";

    // clearing an agent leaves the shared user memory for everyone else
    first.clear();
    assert new EpisodicMemoryStore(Files.createTempDirectory("episodic-agent-three"), shared)
            .byType(MemoryType.user).size() == 1 : "R7.2 — clear must not wipe the shared scope";

    promotesLegacyUserMemories(shared);
}

/// An agent upgrading from the JSON format has its user memories moved to the
/// shared scope, collapsing the copies a repeating model left behind.
// R8.3 — When importing, the BC shall collapse repeated statements into one.
// R8.4 — When importing, the BC shall move what an agent learned about the user into
// the shared scope.
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
            : "R8.3 — one Duke plus the existing shared memory, got: " + upgraded.byType(MemoryType.user);
    assert upgraded.byType(MemoryType.project).size() == 1 : "R8.3 — project note should stay agent-local";
    assert !Files.exists(agentRoot.resolve("episodes").resolve("2026-01-02-030405.html"))
            : "R8.4 — the user memory should have moved out of the agent database";
    assert Files.exists(shared.resolve("episodes").resolve("2026-01-02-030405.html"))
            : "R8.4 — the user memory should have landed in the shared database";
}

// R8.1 — If a kept memory cannot be read, then the BC shall skip it and return the
// rest.
void skipsUnreadableMemories() throws Exception {
    var databaseRoot = Files.createTempDirectory("episodic-unreadable-page");
    var store = new EpisodicMemoryStore(databaseRoot);
    store.store(Episode.of("the readable fact", MemoryType.project));

    Files.writeString(databaseRoot.resolve("episodes").resolve("2026-01-02-030405.html"),
            "<html><body>hand-edited into nonsense <dl><dt>content</body>");

    var reopened = new EpisodicMemoryStore(databaseRoot);

    assert reopened.allEpisodes().size() == 1
            : "R8.1 — the unreadable page costs one memory, not the store, got: " + reopened.allEpisodes();
    assert "the readable fact".equals(reopened.allEpisodes().getFirst().content())
            : "R8.1 — the readable memory must survive its damaged neighbour";
}

// R8.5 — If the store cannot be read at all, then the BC shall report the failure and
// continue with no memory.
void survivesAnUnreadableStore() throws Exception {
    var databaseRoot = Files.createTempDirectory("episodic-unreadable-store");
    new EpisodicMemoryStore(databaseRoot).store(Episode.of("a fact behind a locked door", MemoryType.project));

    var episodes = databaseRoot.resolve("episodes");
    Files.setPosixFilePermissions(episodes, PosixFilePermissions.fromString("---------"));
    try {
        if (isListable(episodes)) {
            // a user who can read whatever the permissions say — root, typically —
            // cannot exercise this statement
            System.out.println("R8.5 — skipped: this user lists a folder it has no permission to");
            return;
        }
        assert new EpisodicMemoryStore(databaseRoot).allEpisodes().isEmpty()
                : "R8.5 — an unreadable store must open empty rather than fail the run";
    } finally {
        Files.setPosixFilePermissions(episodes, PosixFilePermissions.fromString("rwx------"));
    }
}

boolean isListable(Path folder) {
    try (var entries = Files.list(folder)) {
        entries.toList();
        return true;
    } catch (IOException | RuntimeException notListable) {
        return false;
    }
}
