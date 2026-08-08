import java.nio.file.Files;
import java.util.List;
import java.util.TreeMap;

import airhacks.zsmith.htmldb.boundary.HtmlStore;

void main() throws Exception {
    roundTripsArbitraryText();
    generatesBrowsableIndexes();
    appendsWithoutOverwriting();
}

/// The storage format is XHTML, so values must survive escaping, XML line-ending
/// normalization and characters XML cannot represent.
void roundTripsArbitraryText() throws Exception {
    var store = new HtmlStore(Files.createTempDirectory("htmldb-roundtrip"));

    var unrepresentable = String.valueOf((char) 0);
    var hostile = "a & b < c > d\nline2\r\nend " + unrepresentable + " ok 🎉";
    var fields = new TreeMap<String, String>();
    fields.put("content", hostile);
    fields.put("empty", "");
    store.put("notes", "first", fields);

    var restored = store.get("notes", "first").orElseThrow();
    var expected = "a & b < c > d\nline2\r\nend  ok 🎉";
    assert expected.equals(restored.field("content"))
            : "content should round-trip minus unrepresentable characters, got: " + restored.field("content");
    assert "".equals(restored.field("empty")) : "empty value should round-trip, got: " + restored.field("empty");

    assert store.get("notes", "absent").isEmpty() : "absent key should answer empty";
    assert store.keys("unknown").isEmpty() : "unknown table should answer no keys";

    try {
        store.put("notes", "index", fields);
        assert false : "'index' should be rejected as a key";
    } catch (IllegalArgumentException _) {
        // expected
    }
}

void generatesBrowsableIndexes() throws Exception {
    var root = Files.createTempDirectory("htmldb-indexes");
    var store = new HtmlStore(root);
    var fields = new TreeMap<String, String>();
    fields.put("title", "Java 25");
    store.put("talks", "opening", fields);

    var rootIndex = Files.readString(root.resolve("index.html"));
    assert rootIndex.contains("talks/index.html") : "root index should link the table, got: " + rootIndex;
    var tableIndex = Files.readString(root.resolve("talks").resolve("index.html"));
    assert tableIndex.contains("opening.html") : "table index should link the record, got: " + tableIndex;
    assert List.of("talks").equals(store.tables()) : "tables should list talks, got: " + store.tables();

    assert store.remove("talks", "opening") : "remove should report the deletion";
    assert !store.remove("talks", "opening") : "removing twice should report nothing deleted";
    assert store.keys("talks").isEmpty() : "table should be empty after removal";

    store.removeTable("talks");
    assert store.tables().isEmpty() : "tables should be empty after removeTable, got: " + store.tables();
}

void appendsWithoutOverwriting() throws Exception {
    var store = new HtmlStore(Files.createTempDirectory("htmldb-append"));
    var fields = new TreeMap<String, String>();
    fields.put("note", "one");

    var first = store.append("log", "2026-08-08-120000", fields);
    fields.put("note", "two");
    var second = store.append("log", "2026-08-08-120000", fields);

    assert "2026-08-08-120000".equals(first) : "first append should use the given key, got: " + first;
    assert "2026-08-08-120000-2".equals(second) : "colliding append should be suffixed, got: " + second;
    assert store.list("log").size() == 2 : "both records should be stored, got: " + store.list("log").size();
    assert "one".equals(store.get("log", first).orElseThrow().field("note")) : "first record must not be overwritten";
}
