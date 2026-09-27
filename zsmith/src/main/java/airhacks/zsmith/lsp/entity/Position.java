package airhacks.zsmith.lsp.entity;

/// A one-based cursor in a workspace file, as an agent names it. The protocol
/// counts lines and characters from zero, so the translation happens here once.
public record Position(String path, int line, int column) {

    public Position {
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException(
                    "line and column must be at least 1, got line %d column %d".formatted(line, column));
        }
    }

    public int zeroBasedLine() {
        return this.line - 1;
    }

    public int zeroBasedColumn() {
        return this.column - 1;
    }
}
