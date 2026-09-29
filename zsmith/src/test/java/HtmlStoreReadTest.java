import static airhacks.zsmith.htmldb.Requirement.Rn.*;

import airhacks.zsmith.htmldb.Requirement;
import airhacks.zsmith.htmldb.boundary.HtmlStore;
import airhacks.zsmith.htmldb.entity.Entry;

/// Traces htmldb spec R4.1, R4.2, R4.3, R4.4, R4.5, R4.6, R4.7 — see src/main/java/airhacks/zsmith/htmldb/package-info.java

record Answer(Requirement.Rn req, String description, Function<HtmlStore, Object> read, Object expected) {}

record Failure(Requirement.Rn req, String description, Function<HtmlStore, Object> read, String page) {}

static final Entry CLOSING = Entry.of("closing", Map.of("title", "Farewell"));
static final Entry KEYNOTE = Entry.of("keynote", Map.of("title", "Java 25", "speaker", "duke"));
static final Entry OPENING = Entry.of("opening", Map.of("title", "Welcome"));
static final Entry RECORD = Entry.of("record", Map.of("title", "the only record"));

void main() throws IOException {
    var answers = List.of(
            new Answer(R4_1, "get-record of an existing record",
                    store -> store.get("talks", "keynote"), Optional.of(KEYNOTE)),
            new Answer(R4_2, "get-record of an absent key",
                    store -> store.get("talks", "absent"), Optional.empty()),
            new Answer(R4_2, "get-record from an absent table",
                    store -> store.get("unknown", "keynote"), Optional.empty()),
            new Answer(R4_3, "list-keys of records stored out of order",
                    store -> store.keys("talks"), List.of("closing", "keynote", "opening")),
            new Answer(R4_4, "list-records of records stored out of order",
                    store -> store.list("talks"), List.of(CLOSING, KEYNOTE, OPENING)),
            new Answer(R4_5, "list-keys of an absent table",
                    store -> store.keys("unknown"), List.of()),
            new Answer(R4_5, "list-records of an absent table",
                    store -> store.list("unknown"), List.of()),
            new Answer(R4_6, "list-keys of a folder holding other files",
                    store -> store.keys("cluttered"), List.of("record")),
            new Answer(R4_6, "list-records of a folder holding other files",
                    store -> store.list("cluttered"), List.of(RECORD)));

    var failures = List.of(
            new Failure(R4_7, "get-record of a torn page",
                    store -> store.get("torn", "page"), "page.html"),
            new Failure(R4_7, "list-records of a table holding a torn page",
                    store -> store.list("torn"), "page.html"),
            new Failure(R4_7, "get-record of a page with a field name lacking its value",
                    store -> store.get("unpaired", "page"), "page.html"),
            new Failure(R4_7, "get-record of an empty page",
                    store -> store.get("emptied", "page"), "page.html"));

    var root = Files.createTempDirectory("zunit-htmldb-read");
    try {
        var store = populated(root);
        for (var answer : answers) {
            var actual = answer.read().apply(store);
            assert answer.expected().equals(actual)
                    : "%s — %s — %s: expected %s but got %s"
                            .formatted(answer.req(), answer.req().statement(), answer.description(),
                                    answer.expected(), actual);
        }
        for (var failure : failures) {
            var message = failureOf(failure.read(), store);
            assert message.isPresent() && message.get().contains(failure.page())
                    : "%s — %s — %s: expected a failure naming %s but got %s"
                            .formatted(failure.req(), failure.req().statement(), failure.description(),
                                    failure.page(), message);
        }
    } finally {
        deleteRecursively(root);
    }
}

static HtmlStore populated(Path root) throws IOException {
    var store = new HtmlStore(root);
    for (var talk : List.of(OPENING, CLOSING, KEYNOTE)) {
        store.put("talks", talk.key(), talk.fields());
    }

    store.put("cluttered", RECORD.key(), RECORD.fields());
    var cluttered = root.resolve("cluttered");
    Files.writeString(cluttered.resolve("notes.txt"), "not a page");
    Files.writeString(cluttered.resolve(".htmldb4711.tmp"), "an interrupted write");
    Files.copy(cluttered.resolve("record.html"), cluttered.resolve("not a key.html"));
    Files.createDirectory(cluttered.resolve("folder.html"));

    var page = Files.readString(root.resolve("talks").resolve("keynote.html"));
    damage(store, "torn", page.substring(0, page.indexOf("</dd>")));
    damage(store, "unpaired", page.replaceFirst("<dd>[^<]*</dd>", ""));
    damage(store, "emptied", "");
    return store;
}

/// Stands in for the hand edit or the crash that left a page behind which is no longer a record.
static void damage(HtmlStore store, String table, String content) throws IOException {
    store.put(table, "page", KEYNOTE.fields());
    Files.writeString(store.root().resolve(table).resolve("page.html"), content);
}

static Optional<String> failureOf(Function<HtmlStore, Object> read, HtmlStore store) {
    try {
        read.apply(store);
        return Optional.empty();
    } catch (RuntimeException e) {
        return Optional.of(String.valueOf(e.getMessage()));
    }
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
