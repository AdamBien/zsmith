import static airhacks.zsmith.htmldb.Requirement.Rn.*;

import airhacks.zsmith.htmldb.Requirement;
import airhacks.zsmith.htmldb.boundary.HtmlStore;

/// Traces htmldb spec R6.1, R6.2 — see src/main/java/airhacks/zsmith/htmldb/package-info.java

record Case(Requirement.Rn req, String description, List<String> written, List<String> foreignFolders,
        List<String> expected) {}

static final SortedMap<String, String> FIELDS = new TreeMap<>(Map.of("title", "Java 25"));

void main() throws IOException {
    var cases = List.of(
            new Case(R6_1, "tables written out of order",
                    List.of("talks", "attendees", "Rooms", "notes"), List.of(),
                    List.of("Rooms", "attendees", "notes", "talks")),
            new Case(R6_1, "a store without tables",
                    List.of(), List.of(),
                    List.of()),
            new Case(R6_2, "folders without an index page beside the tables",
                    List.of("talks"), List.of("assets", ".git"),
                    List.of("talks")));

    var base = Files.createTempDirectory("zunit-htmldb-tables");
    try {
        for (var c : cases) {
            var root = Files.createTempDirectory(base, "store");
            var store = new HtmlStore(root);
            for (var table : c.written()) {
                store.put(table, "opening", FIELDS);
            }
            for (var folder : c.foreignFolders()) {
                Files.createDirectory(root.resolve(folder));
                Files.writeString(root.resolve(folder).resolve("opening.html"), "not a record");
            }
            Files.writeString(root.resolve("README.md"), "not a table");

            var tables = store.tables();

            assert c.expected().equals(tables)
                    : "%s — %s — %s: expected the tables %s but got %s"
                            .formatted(c.req(), c.req().statement(), c.description(), c.expected(), tables);
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
