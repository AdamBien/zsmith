package airhacks.zsmith.lsp.entity;

import java.util.Comparator;

/// A place in code the server pointed at, one-based. Inside the workspace the
/// path is root-relative and the source line travels along; outside it — a
/// library class, a generated file — the path is whatever the server named
/// and there is no line to quote.
public record Location(String path, int line, int column, String text) implements Comparable<Location> {

    static final Comparator<Location> ORDER = Comparator
            .comparing(Location::path)
            .thenComparingInt(Location::line)
            .thenComparingInt(Location::column);

    public static Location inWorkspace(String path, int line, int column, String text) {
        return new Location(path, line, column, text);
    }

    public static Location outside(String uri, int line, int column) {
        return new Location(uri, line, column, null);
    }

    public boolean insideWorkspace() {
        return this.text != null;
    }

    public String format() {
        return insideWorkspace()
                ? "%s:%d:%d: %s".formatted(this.path, this.line, this.column, this.text.strip())
                : "%s:%d:%d".formatted(this.path, this.line, this.column);
    }

    @Override
    public int compareTo(Location other) {
        return ORDER.compare(this, other);
    }
}
