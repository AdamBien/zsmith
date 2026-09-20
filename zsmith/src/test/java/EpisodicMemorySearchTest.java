import java.nio.file.Files;
import java.util.List;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.episodicmemory.boundary.EpisodicMemoryStore;
import airhacks.zsmith.episodicmemory.control.Relevance;
import airhacks.zsmith.episodicmemory.entity.Episode;
import airhacks.zsmith.episodicmemory.entity.MemoryType;

/// Traces episodicmemory spec R4.1–R4.8 — see
/// src/main/java/airhacks/zsmith/episodicmemory/package-info.java
///
/// The ranking properties are asserted against corpora small enough to reason about
/// by hand: a scoring change that keeps the declared properties leaves these green,
/// and one that breaks a property fails on the property, not on a number.

void main() throws Exception {
    ZCfg.loadBaseConfig("zsmith-test-" + ProcessHandle.current().pid());

    findsWhatAQueryIsAbout();
    prefersTheMemoryCarryingMoreOfTheQuery();
    prefersTheRarerTerm();
    letsRepeatedTermsSaturate();
    matchesByPrefixDisregardingCase();
    breaksTiesByRecency();
    capsTheNumberReturned();
    searchesWithinOneType();
}

EpisodicMemoryStore storeWith(String name, Episode... episodes) throws Exception {
    var store = new EpisodicMemoryStore(Files.createTempDirectory(name));
    for (var episode : episodes) {
        store.store(episode);
    }
    return store;
}

// R4.1 — When a query is supplied, the BC shall return the memories relevant to it,
// most relevant first.
void findsWhatAQueryIsAbout() throws Exception {
    var store = storeWith("search-finds",
            Episode.of("zb builds the project without maven", MemoryType.project),
            Episode.of("the user prefers records over classes", MemoryType.feedback),
            Episode.of("airhacks tv publishes an episode every week", MemoryType.reference));

    var found = store.search("build", 10);

    assert found.size() == 1 : "R4.1 — only the memory about building matches, got: " + contentsOf(found);
    assert found.getFirst().content().startsWith("zb builds")
            : "R4.1 — expected the building memory, got: " + found.getFirst().content();
    assert store.search("records", 10).size() == 1 : "R4.1 — a second query finds its own memory";
}

// R4.2 — When two memories are alike but for the query terms they carry, the BC shall
// rank the one carrying more of them first.
void prefersTheMemoryCarryingMoreOfTheQuery() throws Exception {
    var store = storeWith("search-more-terms",
            Episode.of("maven publishes the module", MemoryType.project),
            Episode.of("maven builds the module", MemoryType.project));

    var found = store.search("maven builds", 10);

    assert found.size() == 2 : "R4.2 — both memories carry a query term, got: " + contentsOf(found);
    assert found.getFirst().content().contains("builds")
            : "R4.2 — the memory carrying both terms leads, got: " + contentsOf(found);
}

// R4.3 — The BC shall weigh a query term carried by few memories above one carried by
// many.
void prefersTheRarerTerm() throws Exception {
    var store = new EpisodicMemoryStore(Files.createTempDirectory("search-rarity"));
    for (var index = 0; index < 8; index++) {
        store.store(Episode.of("memory note number " + index, MemoryType.project));
    }
    store.store(Episode.of("quarkus note number nine", MemoryType.project));

    var found = store.search("memory quarkus", 10);

    assert found.size() == 9 : "R4.3 — every memory carries one of the two terms, got: " + found.size();
    assert found.getFirst().content().startsWith("quarkus")
            : "R4.3 — the rare term must outweigh the one eight memories carry, got: " + found.getFirst().content();
}

// R4.4 — The BC shall let each further occurrence of a term within one memory raise
// that memory's rank by less than the occurrence before it.
void letsRepeatedTermsSaturate() {
    var once = Episode.of("alpha beta gamma delta", MemoryType.project);
    var twice = Episode.of("alpha alpha gamma delta", MemoryType.project);
    var thrice = Episode.of("alpha alpha alpha delta", MemoryType.project);

    var scores = Relevance.scored(List.of(once, twice, thrice), "alpha");

    assert scores.size() == 3 : "R4.4 — every memory carries the term, got: " + scores.size();
    var first = scoreOf(scores, once);
    var second = scoreOf(scores, twice);
    var third = scoreOf(scores, thrice);
    assert third > second && second > first
            : "R4.4 — more occurrences still rank higher, got: %s, %s, %s".formatted(first, second, third);
    assert (second - first) > (third - second)
            : "R4.4 — the third occurrence must add less than the second, got: %s then %s"
                    .formatted(second - first, third - second);
}

double scoreOf(List<Relevance.Scored> scores, Episode episode) {
    return scores.stream()
            .filter(scored -> scored.episode().equals(episode))
            .mapToDouble(Relevance.Scored::score)
            .findFirst()
            .orElseThrow(() -> new AssertionError("R4.4 — unscored memory: " + episode.content()));
}

// R4.5 — The BC shall match a query term against any word beginning with it,
// disregarding case.
void matchesByPrefixDisregardingCase() throws Exception {
    var store = storeWith("search-prefix",
            Episode.of("zb Builds the jar", MemoryType.project));

    assert store.search("BUILD", 10).size() == 1 : "R4.5 — an upper-case prefix must match";
    assert store.search("build", 10).size() == 1 : "R4.5 — a prefix of the stored word must match";
    assert store.search("builds", 10).size() == 1 : "R4.5 — the whole word must match";
    assert store.search("uilds", 10).isEmpty() : "R4.5 — a match begins the word, it is not found inside it";
}

// R4.6 — When memories are equally relevant, the BC shall return the more recently
// kept one first.
void breaksTiesByRecency() throws Exception {
    var store = storeWith("search-ties",
            new Episode("one fact worth keeping", "2026-01-02T03:04:05Z", MemoryType.project),
            new Episode("one fact worth keeping", "2026-06-07T08:09:10Z", MemoryType.reference));

    var found = store.search("fact", 10);

    assert found.size() == 2 : "R4.6 — both memories are equally relevant, got: " + found.size();
    assert found.getFirst().type() == MemoryType.reference
            : "R4.6 — the newer of two equally relevant memories leads, got: " + found.getFirst().timestamp();
}

// R4.7 — Where a count is supplied, the BC shall return at most that many memories.
void capsTheNumberReturned() throws Exception {
    var store = new EpisodicMemoryStore(Files.createTempDirectory("search-limit"));
    for (var index = 0; index < 5; index++) {
        store.store(Episode.of("release note number " + index, MemoryType.project));
    }

    assert store.search("release", 2).size() == 2 : "R4.7 — the count caps the result";
    assert store.search("release", 99).size() == 5 : "R4.7 — a count beyond the corpus returns what there is";
    assert store.search("release", 0).isEmpty() : "R4.7 — a count of zero returns nothing";
}

// R4.8 — Where a type is supplied, the BC shall rank only the memories carrying it.
void searchesWithinOneType() throws Exception {
    var store = storeWith("search-typed",
            Episode.of("the build runs with zb", MemoryType.project),
            Episode.of("the build output belongs in zbo", MemoryType.feedback));

    var project = store.search("build", MemoryType.project, 10);

    assert project.size() == 1 : "R4.8 — only the project memory may be ranked, got: " + contentsOf(project);
    assert project.getFirst().type() == MemoryType.project : "R4.8 — the returned memory carries the type";
    assert store.search("build", 10).size() == 2 : "R4.8 — without a type both memories are ranked";
}

List<String> contentsOf(List<Episode> episodes) {
    return episodes.stream().map(Episode::content).toList();
}
