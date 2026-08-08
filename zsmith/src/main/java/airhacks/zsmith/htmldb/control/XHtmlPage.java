package airhacks.zsmith.htmldb.control;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

import module java.xml;

/// Renders and parses the pages of the store. Every page is valid HTML5 and
/// well-formed XML at the same time, which is what allows the browsable format to
/// double as the parseable one.
public interface XHtmlPage {

    String INDEX_PAGE = "index.html";
    String PAGE_SUFFIX = ".html";

    DocumentBuilderFactory FACTORY = hardenedFactory();

    static String record(String table, String key, SortedMap<String, String> fields) {
        var definitions = fields.entrySet().stream()
                .map(XHtmlPage::definition)
                .collect(Collectors.joining("\n"));
        var list = fields.isEmpty() ? "  <dl></dl>" : "  <dl>\n%s\n  </dl>".formatted(definitions);
        return page(key, breadcrumb(INDEX_PAGE, table), list + "\n" + updatedFooter());
    }

    static String tableIndex(String table, String rootTitle, List<String> keys) {
        var links = keys.stream()
                .map(XHtmlPage::recordLink)
                .collect(Collectors.joining("\n"));
        return page(table, breadcrumb("../" + INDEX_PAGE, rootTitle), navigation(links));
    }

    static String rootIndex(String title, List<String> tables) {
        var links = tables.stream()
                .map(XHtmlPage::tableLink)
                .collect(Collectors.joining("\n"));
        return page(title, "", navigation(links));
    }

    /// Reconstructs a record by pairing the `dt` and `dd` elements of the page.
    static SortedMap<String, String> fields(Path page) {
        var document = parse(page);
        var names = document.getElementsByTagName("dt");
        var values = document.getElementsByTagName("dd");
        var fields = new TreeMap<String, String>();
        for (var index = 0; index < Math.min(names.getLength(), values.getLength()); index++) {
            fields.put(names.item(index).getTextContent(), values.item(index).getTextContent());
        }
        return fields;
    }

    static Document parse(Path page) {
        try {
            return builder().parse(page.toFile());
        } catch (SAXException e) {
            throw new IllegalStateException("malformed page " + page + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// A [DocumentBuilder] is not thread safe and cannot be shared, but the factory
    /// creating it is expensive enough to be worth caching.
    static DocumentBuilder builder() {
        synchronized (FACTORY) {
            try {
                return FACTORY.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new IllegalStateException("cannot create XML parser: " + e.getMessage(), e);
            }
        }
    }

    static DocumentBuilderFactory hardenedFactory() {
        var factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("cannot configure XML parser: " + e.getMessage(), e);
        }
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setXIncludeAware(false);
        return factory;
    }

    static String definition(Map.Entry<String, String> field) {
        return "    <dt>%s</dt>\n    <dd>%s</dd>".formatted(escape(field.getKey()), escape(field.getValue()));
    }

    static String recordLink(String key) {
        return link(key + PAGE_SUFFIX, key);
    }

    static String tableLink(String table) {
        return link(table + "/" + INDEX_PAGE, table);
    }

    static String link(String href, String label) {
        return "      <li><a href=\"%s\">%s</a></li>".formatted(href, escape(label));
    }

    static String navigation(String links) {
        if (links.isEmpty()) {
            return "  <nav><ul></ul></nav>";
        }
        return "  <nav>\n    <ul>\n%s\n    </ul>\n  </nav>".formatted(links);
    }

    static String updatedFooter() {
        var updated = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return """
                  <footer>
                    <p>updated <time datetime="%s">%s</time></p>
                  </footer>\
                """.formatted(updated, updated);
    }

    static String breadcrumb(String href, String label) {
        return """
                <header>
                  <nav><a href="%s">↑ %s</a></nav>
                </header>
                """.formatted(href, escape(label));
    }

    static String page(String title, String breadcrumb, String content) {
        return """
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <title>%s</title>
                  <style>
                    :root { color-scheme: light dark; }
                    body { font-family: system-ui, sans-serif; line-height: 1.6; max-width: 40rem; margin: 2rem auto; padding: 0 1rem; }
                    dl { display: grid; grid-template-columns: max-content 1fr; gap: 0.3rem 1.5rem; }
                    dt { font-weight: 600; }
                    dd { margin: 0; white-space: pre-wrap; }
                    nav ul { list-style: none; padding: 0; }
                    footer { font-size: 0.8rem; opacity: 0.6; }
                  </style>
                </head>
                <body>
                %s<main>
                  <h1>%s</h1>
                %s
                </main>
                </body>
                </html>
                """.formatted(escape(title), breadcrumb, escape(title), content);
    }

    /// Only `&`, `<` and `>` need escaping inside element text. A literal carriage
    /// return would be silently normalized to a line feed by any XML parser, so it
    /// is written as a character reference, which is exempt from that
    /// normalization, to survive the round trip.
    static String escape(String text) {
        return sanitized(text)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\r", "&#13;");
    }

    /// Drops the characters XML 1.0 cannot represent at all — not even as a
    /// character reference. Stored values are arbitrary text, so without this a
    /// single stray control character would render the whole page unparseable.
    static String sanitized(String text) {
        if (text.codePoints().allMatch(XHtmlPage::isRepresentable)) {
            return text;
        }
        return text.codePoints()
                .filter(XHtmlPage::isRepresentable)
                .mapToObj(Character::toString)
                .collect(Collectors.joining());
    }

    static boolean isRepresentable(int codePoint) {
        return switch (codePoint) {
            case '\t', '\n', '\r' -> true;
            default -> codePoint >= 0x20 && codePoint <= 0xD7FF
                    || codePoint >= 0xE000 && codePoint <= 0xFFFD
                    || codePoint >= 0x10000;
        };
    }
}
