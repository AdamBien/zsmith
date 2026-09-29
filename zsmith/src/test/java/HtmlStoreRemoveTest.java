import static airhacks.zsmith.htmldb.Requirement.Rn.*;

import airhacks.zsmith.htmldb.Requirement;
import airhacks.zsmith.htmldb.boundary.HtmlStore;

/// Traces htmldb spec R5.1, R5.2, R5.3, R5.4 — see src/main/java/airhacks/zsmith/htmldb/package-info.java
/// Every case starts from the table talks holding the records closing and opening.

record Case(Requirement.Rn req, String description, Function<HtmlStore, Object> removal, Object answer,
        List<String> tablesLeft, List<String> talksLeft) {}

static final String COMPLETED = "completed";
static final SortedMap<String, String> FIELDS = new TreeMap<>(Map.of("title", "Java 25"));

void main() throws IOException {
    var cases = List.of(
            new Case(R5_1, "remove-record of an existing record",
                    store -> store.remove("talks", "opening"), true,
                    List.of("talks"), List.of("closing")),
            new Case(R5_2, "remove-record of an absent key",
                    store -> store.remove("talks", "absent"), false,
                    List.of("talks"), List.of("closing", "opening")),
            new Case(R5_2, "remove-record from an absent table",
                    store -> store.remove("unknown", "opening"), false,
                    List.of("talks"), List.of("closing", "opening")),
            new Case(R5_3, "remove-table of a table holding records",
                    store -> removeTable(store, "talks"), COMPLETED,
                    List.of(), List.of()),
            new Case(R5_4, "remove-table of an absent table",
                    store -> removeTable(store, "unknown"), COMPLETED,
                    List.of("talks"), List.of("closing", "opening")));

    var base = Files.createTempDirectory("zunit-htmldb-remove");
    try {
        for (var c : cases) {
            var root = Files.createTempDirectory(base, "store");
            var store = new HtmlStore(root);
            store.put("talks", "opening", FIELDS);
            store.put("talks", "closing", FIELDS);

            var answer = c.removal().apply(store);

            assert c.answer().equals(answer)
                    : "%s — %s — %s: expected the answer %s but got %s"
                            .formatted(c.req(), c.req().statement(), c.description(), c.answer(), answer);
            assert c.tablesLeft().equals(store.tables())
                    : "%s — %s — %s: expected the tables %s left but got %s"
                            .formatted(c.req(), c.req().statement(), c.description(), c.tablesLeft(), store.tables());
            assert c.talksLeft().equals(store.keys("talks"))
                    : "%s — %s — %s: expected the records %s left but got %s"
                            .formatted(c.req(), c.req().statement(), c.description(), c.talksLeft(), store.keys("talks"));
            var folderLeft = Files.exists(root.resolve("talks"));
            assert folderLeft == c.tablesLeft().contains("talks")
                    : "%s — %s — %s: expected the folder of talks %s"
                            .formatted(c.req(), c.req().statement(), c.description(), folderLeft ? "gone" : "kept");
        }
    } finally {
        deleteRecursively(base);
    }
}

static Object removeTable(HtmlStore store, String table) {
    store.removeTable(table);
    return COMPLETED;
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
