import static airhacks.zsmith.htmldb.Requirement.Rn.*;

import module java.xml;

import airhacks.zsmith.htmldb.Requirement;
import airhacks.zsmith.htmldb.boundary.HtmlStore;

/// Traces htmldb spec R7.1, R7.2, R7.3, R7.4 — see src/main/java/airhacks/zsmith/htmldb/package-info.java
/// Every page is read the way a foreign tool would: from the folder, with a plain XML parser.

record Case(Requirement.Rn req, String description, String page, Function<Document, Object> observation,
        Object expected) {}

static final SortedMap<String, String> OPENING = new TreeMap<>(Map.of(
        "title", "Java 25 <& beyond>",
        "abstract", "first line\nsecond line"));
static final SortedMap<String, String> FIELDS = new TreeMap<>(Map.of("title", "Java 25"));

void main() throws Exception {
    var cases = List.of(
            new Case(R7_1, "the record page declares itself HTML", "conference/talks/opening.html",
                    page -> page.getDoctype().getName(), "html"),
            new Case(R7_1, "the record page is an XHTML document", "conference/talks/opening.html",
                    page -> page.getDocumentElement().getTagName() + " " + page.getDocumentElement().getAttribute("xmlns"),
                    "html http://www.w3.org/1999/xhtml"),
            new Case(R7_1, "the record page is titled after its key", "conference/talks/opening.html",
                    page -> textsOf(page, "title"), List.of("opening")),
            new Case(R7_1, "the record page holds the field names as definition terms", "conference/talks/opening.html",
                    page -> textsOf(page, "dt"), List.copyOf(OPENING.keySet())),
            new Case(R7_1, "the record page holds the field values as definitions", "conference/talks/opening.html",
                    page -> textsOf(page, "dd"), List.copyOf(OPENING.values())),
            new Case(R7_2, "the table index links every stored record and no removed one", "conference/talks/index.html",
                    page -> recordLinksOf(page), List.of("keynote.html", "opening.html")),
            new Case(R7_2, "the root index links every table", "conference/index.html",
                    page -> linksOf(page), List.of("attendees/index.html", "talks/index.html")),
            new Case(R7_3, "the record page links back to its table index", "conference/talks/opening.html",
                    page -> linksOf(page), List.of("index.html")),
            new Case(R7_3, "the table index links back to the root index", "conference/talks/index.html",
                    page -> linksOf(page).contains("../index.html"), true),
            new Case(R7_4, "the root index is titled after the folder", "conference/index.html",
                    page -> textsOf(page, "title"), List.of("conference")),
            new Case(R7_4, "the root index is headed after the folder", "conference/index.html",
                    page -> textsOf(page, "h1"), List.of("conference")),
            new Case(R7_4, "the root index of a store opened at a path that is not normalized", "library/index.html",
                    page -> textsOf(page, "title"), List.of("library")));

    var base = Files.createTempDirectory("zunit-htmldb-browsable");
    try {
        var conference = new HtmlStore(base.resolve("conference"));
        conference.put("talks", "opening", OPENING);
        conference.put("talks", "keynote", FIELDS);
        conference.put("talks", "cancelled", FIELDS);
        conference.put("attendees", "duke", FIELDS);
        conference.remove("talks", "cancelled");

        var library = new HtmlStore(Files.createDirectory(base.resolve("library")).resolve("."));
        library.put("books", "effective-java", FIELDS);

        for (var c : cases) {
            var page = base.resolve(c.page());
            var observed = c.observation().apply(parse(page, c));
            assert c.expected().equals(observed)
                    : "%s — %s — %s: expected %s in %s but got %s"
                            .formatted(c.req(), c.req().statement(), c.description(), c.expected(), c.page(), observed);
        }
    } finally {
        deleteRecursively(base);
    }
}

static Document parse(Path page, Case c) throws Exception {
    var content = Files.readString(page);
    assert content.startsWith("<!DOCTYPE html>")
            : "%s — %s — expected %s to start with the HTML doctype but it starts with: %s"
                    .formatted(c.req(), c.req().statement(), c.page(), content.lines().findFirst());
    try {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(page.toFile());
    } catch (SAXException e) {
        throw new AssertionError("%s — %s — expected %s to be well-formed XML: %s"
                .formatted(c.req(), c.req().statement(), c.page(), e.getMessage()), e);
    }
}

static List<String> textsOf(Document page, String tag) {
    var elements = page.getElementsByTagName(tag);
    return IntStream.range(0, elements.getLength())
            .mapToObj(index -> elements.item(index).getTextContent())
            .toList();
}

static List<String> linksOf(Document page) {
    var anchors = page.getElementsByTagName("a");
    return IntStream.range(0, anchors.getLength())
            .mapToObj(index -> ((Element) anchors.item(index)).getAttribute("href"))
            .sorted()
            .toList();
}

/// The links of an index minus the one leading back up.
static List<String> recordLinksOf(Document page) {
    return linksOf(page).stream()
            .filter(link -> !link.startsWith("../"))
            .toList();
}

static void deleteRecursively(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
        files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
}
