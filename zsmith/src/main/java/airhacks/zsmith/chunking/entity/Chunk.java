package airhacks.zsmith.chunking.entity;

/// One piece of a split text: its 1-based position, its text, and the separator the cut after
/// it fell on — kept out of the text so the reply can be joined back on it. The last chunk, and
/// a chunk cut at exactly the budget, carry an empty separator.
public record Chunk(int position, String text, String separator) {

    public boolean blank() {
        return this.text.isBlank();
    }
}
