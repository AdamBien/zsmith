import static airhacks.zsmith.htmldb.Requirement.Rn.*;

import airhacks.zsmith.htmldb.Requirement;
import airhacks.zsmith.htmldb.boundary.HtmlStore;

/// Traces htmldb spec R1.1, R1.2, R1.3, R1.4 — see src/main/java/airhacks/zsmith/htmldb/package-info.java
/// Every operation is invoked once per role a name can take in it: as the table, and as the key.

record Operation(String description, BiConsumer<HtmlStore, String> invocation) {}

record Case(Requirement.Rn req, String name, boolean accepted, List<Operation> operations) {}

static final SortedMap<String, String> FIELDS = new TreeMap<>(Map.of("title", "Java 25"));

static final List<Operation> WRITING = List.of(
        new Operation("put-record, name as table", (store, name) -> store.put(name, "opening", FIELDS)),
        new Operation("put-record, name as key", (store, name) -> store.put("talks", name, FIELDS)),
        new Operation("append-record, name as table", (store, name) -> store.append(name, "opening", FIELDS)),
        new Operation("append-record, name as key", (store, name) -> store.append("talks", name, FIELDS)));

static final List<Operation> READING_AND_REMOVING = List.of(
        new Operation("get-record, name as table", (store, name) -> store.get(name, "opening")),
        new Operation("get-record, name as key", (store, name) -> store.get("talks", name)),
        new Operation("list-keys, name as table", (store, name) -> store.keys(name)),
        new Operation("list-records, name as table", (store, name) -> store.list(name)),
        new Operation("remove-record, name as table", (store, name) -> store.remove(name, "opening")),
        new Operation("remove-record, name as key", (store, name) -> store.remove("talks", name)),
        new Operation("remove-table, name as table", (store, name) -> store.removeTable(name)));

void main() throws IOException {
    var cases = List.of(
            new Case(R1_1, "notes", true, WRITING),
            new Case(R1_1, "Notes2026", true, WRITING),
            new Case(R1_1, "2026-08-08-120000", true, WRITING),
            new Case(R1_1, "_draft", true, WRITING),
            new Case(R1_1, "a_b-c", true, WRITING),
            new Case(R1_2, null, false, WRITING),
            new Case(R1_2, "", false, WRITING),
            new Case(R1_2, "-leading", false, WRITING),
            new Case(R1_2, "two words", false, WRITING),
            new Case(R1_2, "dotted.name", false, WRITING),
            new Case(R1_2, "..", false, WRITING),
            new Case(R1_2, "nested/name", false, WRITING),
            new Case(R1_2, "nested\\name", false, WRITING),
            new Case(R1_2, "../outside", false, WRITING),
            new Case(R1_3, "index", false, WRITING),
            new Case(R1_4, "notes", true, READING_AND_REMOVING),
            new Case(R1_4, null, false, READING_AND_REMOVING),
            new Case(R1_4, "", false, READING_AND_REMOVING),
            new Case(R1_4, "-leading", false, READING_AND_REMOVING),
            new Case(R1_4, "../outside", false, READING_AND_REMOVING),
            new Case(R1_4, "index", false, READING_AND_REMOVING));

    var base = Files.createTempDirectory("zunit-htmldb-names");
    try {
        for (var c : cases) {
            for (var operation : c.operations()) {
                var root = Files.createTempDirectory(base, "store");
                var store = new HtmlStore(root);
                store.put("talks", "opening", FIELDS);
                // the page a hostile name such as ../outside would resolve to
                var outside = Files.copy(root.resolve("talks").resolve("opening.html"), root.resolve("outside.html"));

                var accepted = accepts(store, operation, c.name());

                assert accepted == c.accepted()
                        : "%s — %s — expected the name '%s' %s by %s but it was %s"
                                .formatted(c.req(), c.req().statement(), c.name(), outcome(c.accepted()),
                                        operation.description(), outcome(accepted));
                assert Files.exists(outside)
                        : "%s — %s — expected the page outside the table untouched by %s with the name '%s'"
                                .formatted(c.req(), c.req().statement(), operation.description(), c.name());
            }
        }
    } finally {
        deleteRecursively(base);
    }
}

static boolean accepts(HtmlStore store, Operation operation, String name) {
    try {
        operation.invocation().accept(store, name);
        return true;
    } catch (IllegalArgumentException rejected) {
        return false;
    }
}

static String outcome(boolean accepted) {
    return accepted ? "accepted" : "rejected";
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
