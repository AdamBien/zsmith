import static airhacks.zsmith.htmldb.Requirement.Rn.*;

import airhacks.zsmith.htmldb.Requirement;
import airhacks.zsmith.htmldb.boundary.HtmlStore;

/// Traces htmldb spec R2.1, R2.2, R2.3, R2.4, R2.5 — see src/main/java/airhacks/zsmith/htmldb/package-info.java

record Case(Requirement.Rn req, String description, Optional<Map<String, String>> existing,
        Map<String, String> fields, Map<String, String> expected) {

    static Case unchanged(Requirement.Rn req, String description, Map<String, String> fields) {
        return new Case(req, description, Optional.empty(), fields, fields);
    }
}

// characters XML 1.0 cannot carry, not even as a character reference
static final String NUL = Character.toString(0x0);
static final String FORM_FEED = "\f";
static final String NONCHARACTER = Character.toString(0xFFFF);
static final String LONE_SURROGATE = String.valueOf((char) 0xD800);

void main() throws Exception {
    var base = Files.createTempDirectory("zunit-htmldb-put");
    try {
        storesRecords(base);
        writesAtomically(base);
    } finally {
        deleteRecursively(base);
    }
}

void storesRecords(Path base) throws IOException {
    var cases = List.of(
            Case.unchanged(R2_1, "a record in a table that does not exist yet",
                    Map.of("title", "Java 25", "speaker", "duke")),
            Case.unchanged(R2_1, "a record without fields", Map.of()),
            new Case(R2_2, "fewer fields than the record held",
                    Optional.of(Map.of("title", "Java 24", "speaker", "duke")),
                    Map.of("title", "Java 25"),
                    Map.of("title", "Java 25")),
            new Case(R2_2, "other fields than the record held",
                    Optional.of(Map.of("title", "Java 25")),
                    Map.of("room", "hall 1"),
                    Map.of("room", "hall 1")),
            Case.unchanged(R2_3, "an empty value", Map.of("empty", "")),
            Case.unchanged(R2_3, "line breaks of every flavour", Map.of("lines", "unix\nwindows\r\nclassic\rend")),
            Case.unchanged(R2_3, "markup characters",
                    Map.of("markup", "<dd>a & b</dd> ]]> &amp; &#13; \"double\" 'single' <!-- -->")),
            Case.unchanged(R2_3, "characters beyond the basic plane", Map.of("beyond", "party 🎉 clef 𝄞")),
            Case.unchanged(R2_3, "surrounding whitespace", Map.of("padded", "  \t leading and trailing \n ")),
            Case.unchanged(R2_3, "any text as a field name", Map.of("<b>name</b> & co\r\n🎉", "value")),
            Case.unchanged(R2_3, "an empty field name", Map.of("", "value")),
            new Case(R2_4, "unrepresentable characters in a value", Optional.empty(),
                    Map.of("content", "be" + NUL + "fo" + FORM_FEED + "re " + NONCHARACTER + "and" + LONE_SURROGATE + " after 🎉"),
                    Map.of("content", "before and after 🎉")),
            new Case(R2_4, "unrepresentable characters in a field name", Optional.empty(),
                    Map.of("con" + NUL + "tent" + LONE_SURROGATE, "value"),
                    Map.of("content", "value")));

    for (var c : cases) {
        var store = new HtmlStore(Files.createTempDirectory(base, "store"));
        c.existing().ifPresent(existing -> store.put("talks", "opening", new TreeMap<>(existing)));

        store.put("talks", "opening", new TreeMap<>(c.fields()));

        var stored = store.get("talks", "opening").map(entry -> entry.fields());
        assert stored.equals(Optional.of(new TreeMap<>(c.expected())))
                : "%s — %s — %s: expected the fields %s but got %s"
                        .formatted(c.req(), c.req().statement(), c.description(), c.expected(), stored);
        assert List.of("talks").equals(store.tables())
                : "%s — %s — %s: expected the table talks but got %s"
                        .formatted(c.req(), c.req().statement(), c.description(), store.tables());
    }
}

/// A reader racing a writer sees either the record as it was or as it is now, and
/// the folder holds nothing but pages once the writer is done.
void writesAtomically(Path base) throws Exception {
    var root = Files.createTempDirectory(base, "store");
    var store = new HtmlStore(root);
    var brief = "brief";
    var extensive = "extensive ".repeat(50_000);
    store.put("talks", "opening", new TreeMap<>(Map.of("content", brief)));

    try (var executor = Executors.newSingleThreadExecutor()) {
        var writer = executor.submit(() -> {
            for (var round = 0; round < 50; round++) {
                var content = round % 2 == 0 ? extensive : brief;
                store.put("talks", "opening", new TreeMap<>(Map.of("content", content)));
            }
        });
        while (!writer.isDone()) {
            var observed = observe(store);
            assert brief.equals(observed) || extensive.equals(observed)
                    : "%s — %s — expected a complete record but observed %d characters"
                            .formatted(R2_5, R2_5.statement(), observed.length());
        }
        writer.get();
    }

    var pages = fileNames(root.resolve("talks"));
    assert List.of("index.html", "opening.html").equals(pages)
            : "%s — %s — expected only the record and the index in the table folder but got %s"
                    .formatted(R2_5, R2_5.statement(), pages);
    var content = fileNames(root);
    assert List.of("index.html", "talks").equals(content)
            : "%s — %s — expected only the index and the table in the root folder but got %s"
                    .formatted(R2_5, R2_5.statement(), content);
}

static String observe(HtmlStore store) {
    try {
        return store.get("talks", "opening").orElseThrow().field("content");
    } catch (RuntimeException e) {
        throw new AssertionError("%s — %s — the reader observed a broken page: %s"
                .formatted(R2_5, R2_5.statement(), e), e);
    }
}

static List<String> fileNames(Path folder) throws IOException {
    try (var files = Files.list(folder)) {
        return files.map(file -> file.getFileName().toString()).sorted().toList();
    }
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
