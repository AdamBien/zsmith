package airhacks.zsmith.tools.control;

/// A window into a file's lines, counted from one — the unit `search_files`
/// already reports matches in, so a match at `Foo.java:42` can be read back
/// without translating between a zero- and a one-based world.
public record LineRange(int offset, int limit) {

    /// A limit meaning "to the last line", so an offset alone reads a file's tail.
    public static final int TO_END = -1;

    public LineRange {
        if (offset < 1) {
            throw new IllegalArgumentException("offset is one-based, was: " + offset);
        }
        if (limit != TO_END && limit < 1) {
            throw new IllegalArgumentException("limit is at least one line, was: " + limit);
        }
    }

    public static LineRange wholeFile() {
        return new LineRange(1, TO_END);
    }

    /// True when the window imposes no restriction, so the caller asked for
    /// content rather than a slice.
    public boolean coversWholeFile() {
        return this.offset == 1 && this.limit == TO_END;
    }

    /// The one-based line the window ends on, clamped to a file of `totalLines`.
    public int lastLine(int totalLines) {
        if (this.limit == TO_END) {
            return totalLines;
        }
        return Math.min(this.offset + this.limit - 1, totalLines);
    }

    public boolean startsBeyond(int totalLines) {
        return this.offset > totalLines;
    }
}
