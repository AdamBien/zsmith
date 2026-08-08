package airhacks.zsmith.htmldb.boundary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.regex.Pattern;

import airhacks.zsmith.htmldb.control.XHtmlPage;
import airhacks.zsmith.htmldb.entity.Entry;

/// A folder of browsable XHTML pages used as a key-value store: a table is a
/// folder, a record is a page, and the generated `index.html` pages turn the whole
/// database into a website that can be read, diffed and hand-edited without going
/// through this component.
public record HtmlStore(Path root) {

    static final String RESERVED_NAME = "index";
    static final String TEMP_PREFIX = ".htmldb";
    static final Pattern NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_-]*");

    public HtmlStore {
        createDirectories(root);
    }

    /// Names the database after the folder holding it — the heading and breadcrumb
    /// target of the generated navigation.
    String title() {
        return this.root.getFileName().toString();
    }

    /// Replaces the record under `key`, creating the table if needed.
    public void put(String table, String key, SortedMap<String, String> fields) {
        validate(table, "table");
        validate(key, "key");
        createDirectories(this.root.resolve(table));
        atomicWrite(recordPage(table, key), XHtmlPage.record(table, key, fields));
        updateTableIndex(table);
        updateRootIndex();
    }

    /// Stores the record under `key`, or under `key-2`, `key-3`… when a record of
    /// that key already exists, and answers the key it ended up under. Keeps
    /// appends from silently overwriting each other when keys are derived from a
    /// coarse timestamp.
    public String append(String table, String key, SortedMap<String, String> fields) {
        validate(table, "table");
        validate(key, "key");
        var unique = key;
        for (var counter = 2; Files.exists(recordPage(table, unique)); counter++) {
            unique = key + "-" + counter;
        }
        put(table, unique, fields);
        return unique;
    }

    public Optional<Entry> get(String table, String key) {
        var page = recordPage(table, key);
        if (!Files.isRegularFile(page)) {
            return Optional.empty();
        }
        return Optional.of(new Entry(key, XHtmlPage.fields(page)));
    }

    /// Answers the keys of the table in ascending order, or nothing at all for a
    /// table that does not exist yet.
    public List<String> keys(String table) {
        var folder = this.root.resolve(table);
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        return names(folder).stream()
                .filter(HtmlStore::isRecordPage)
                .map(HtmlStore::keyOf)
                .sorted()
                .toList();
    }

    public List<Entry> list(String table) {
        return keys(table).stream()
                .map(key -> new Entry(key, XHtmlPage.fields(recordPage(table, key))))
                .toList();
    }

    public boolean remove(String table, String key) {
        var page = recordPage(table, key);
        if (!Files.isRegularFile(page)) {
            return false;
        }
        delete(page);
        updateTableIndex(table);
        updateRootIndex();
        return true;
    }

    public void removeTable(String table) {
        var folder = this.root.resolve(table);
        if (!Files.isDirectory(folder)) {
            return;
        }
        for (var page : contentOf(folder)) {
            delete(page);
        }
        delete(folder);
        updateRootIndex();
    }

    public List<String> tables() {
        return contentOf(this.root).stream()
                .filter(Files::isDirectory)
                .filter(HtmlStore::isTable)
                .map(folder -> folder.getFileName().toString())
                .sorted()
                .toList();
    }

    void updateTableIndex(String table) {
        atomicWrite(this.root.resolve(table).resolve(XHtmlPage.INDEX_PAGE),
                XHtmlPage.tableIndex(table, title(), keys(table)));
    }

    void updateRootIndex() {
        atomicWrite(this.root.resolve(XHtmlPage.INDEX_PAGE), XHtmlPage.rootIndex(title(), tables()));
    }

    Path recordPage(String table, String key) {
        return this.root.resolve(table).resolve(key + XHtmlPage.PAGE_SUFFIX);
    }

    /// Writes through a temporary file in the target folder so that readers — a
    /// browser, git, another agent — never observe a half-written page.
    void atomicWrite(Path target, String content) {
        try {
            var temp = Files.createTempFile(target.getParent(), TEMP_PREFIX, ".tmp");
            Files.writeString(temp, content);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    List<Path> contentOf(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (var paths = Files.list(folder)) {
            return paths.toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    List<String> names(Path folder) {
        return contentOf(folder).stream()
                .map(path -> path.getFileName().toString())
                .toList();
    }

    void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void createDirectories(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean isTable(Path folder) {
        return Files.isRegularFile(folder.resolve(XHtmlPage.INDEX_PAGE));
    }

    static boolean isRecordPage(String fileName) {
        return fileName.endsWith(XHtmlPage.PAGE_SUFFIX) && !fileName.equals(XHtmlPage.INDEX_PAGE);
    }

    static String keyOf(String fileName) {
        return fileName.substring(0, fileName.length() - XHtmlPage.PAGE_SUFFIX.length());
    }

    /// Table and key names become folder and file names, so they are restricted to
    /// a filename-safe grammar; `index` is reserved for the generated navigation.
    static void validate(String name, String kind) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid %s name: %s (allowed: letters, digits, _ and -)".formatted(kind, name));
        }
        if (RESERVED_NAME.equals(name)) {
            throw new IllegalArgumentException("'%s' is a reserved %s name".formatted(RESERVED_NAME, kind));
        }
    }
}
