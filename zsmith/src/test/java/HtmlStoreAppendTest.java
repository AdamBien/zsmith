import static airhacks.zsmith.htmldb.Requirement.Rn.*;

import airhacks.zsmith.htmldb.Requirement;
import airhacks.zsmith.htmldb.boundary.HtmlStore;

/// Traces htmldb spec R3.1, R3.2, R3.3 — see src/main/java/airhacks/zsmith/htmldb/package-info.java

record Case(Requirement.Rn req, String description, List<String> existing, String expectedKey) {}

static final String KEY = "2026-08-08-120000";
static final SortedMap<String, String> EXISTING = new TreeMap<>(Map.of("note", "existing", "author", "duke"));
static final SortedMap<String, String> APPENDED = new TreeMap<>(Map.of("note", "appended"));

void main() throws IOException {
    var cases = List.of(
            new Case(R3_1, "a free key", List.of(), KEY),
            new Case(R3_2, "a taken key", List.of(KEY), KEY + "-2"),
            new Case(R3_2, "a taken key and a taken suffix", List.of(KEY, KEY + "-2"), KEY + "-3"),
            new Case(R3_2, "a free suffix below a taken one", List.of(KEY, KEY + "-3"), KEY + "-2"),
            new Case(R3_3, "a taken key", List.of(KEY), KEY + "-2"),
            new Case(R3_3, "a taken key and a taken suffix", List.of(KEY, KEY + "-2"), KEY + "-3"));

    var base = Files.createTempDirectory("zunit-htmldb-append");
    try {
        for (var c : cases) {
            var store = new HtmlStore(Files.createTempDirectory(base, "store"));
            c.existing().forEach(key -> store.put("log", key, EXISTING));

            var answered = store.append("log", KEY, APPENDED);

            assert c.expectedKey().equals(answered)
                    : "%s — %s — %s: expected the key %s but got %s"
                            .formatted(c.req(), c.req().statement(), c.description(), c.expectedKey(), answered);
            var appended = store.get("log", answered).map(entry -> entry.fields());
            assert appended.equals(Optional.of(APPENDED))
                    : "%s — %s — %s: expected %s under %s but got %s"
                            .formatted(c.req(), c.req().statement(), c.description(), APPENDED, answered, appended);
            for (var key : c.existing()) {
                var existing = store.get("log", key).map(entry -> entry.fields());
                assert existing.equals(Optional.of(EXISTING))
                        : "%s — %s — %s: expected the record under %s untouched but got %s"
                                .formatted(c.req(), c.req().statement(), c.description(), key, existing);
            }
            var expectedKeys = Stream.concat(c.existing().stream(), Stream.of(c.expectedKey())).sorted().toList();
            assert expectedKeys.equals(store.keys("log"))
                    : "%s — %s — %s: expected the keys %s but got %s"
                            .formatted(c.req(), c.req().statement(), c.description(), expectedKeys, store.keys("log"));
        }
    } finally {
        deleteRecursively(base);
    }
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
