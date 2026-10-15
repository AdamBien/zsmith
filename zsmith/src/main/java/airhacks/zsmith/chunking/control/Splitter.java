package airhacks.zsmith.chunking.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import airhacks.zsmith.chunking.entity.Chunk;

/// Cuts a text into chunks of at most a budget of characters, each at the coarsest boundary the
/// budget allows: a paragraph break, else a sentence end, else whitespace, else the budget itself.
public interface Splitter {

    /// A line break, optionally followed by whitespace, then another line break — the blank line
    /// between paragraphs, however many there are.
    Pattern PARAGRAPH_BREAK = Pattern.compile("\\R\\s*\\R");

    /// The whitespace after a sentence's closing mark; the mark stays with its sentence.
    Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    Pattern WHITESPACE = Pattern.compile("\\s+");

    static List<Chunk> split(String text, int budget) {
        var chunks = new ArrayList<Chunk>();
        var rest = text;
        while (rest.length() > budget) {
            var cut = cut(rest, budget);
            chunks.add(new Chunk(chunks.size() + 1, rest.substring(0, cut.start()), rest.substring(cut.start(), cut.end())));
            rest = rest.substring(cut.end());
        }
        chunks.add(new Chunk(chunks.size() + 1, rest, ""));
        return chunks;
    }

    static Cut cut(String text, int budget) {
        return lastWithin(PARAGRAPH_BREAK, text, budget)
                .or(() -> lastWithin(SENTENCE_END, text, budget))
                .or(() -> lastWithin(WHITESPACE, text, budget))
                .orElse(new Cut(budget, budget));
    }

    /// Where a separator lies: from its first character up to the first one after it.
    record Cut(int start, int end) {
    }

    /// The last separator starting within the budget, so the text before it fits. A separator at
    /// the very start would leave an empty chunk, so it does not count.
    static Optional<Cut> lastWithin(Pattern separator, String text, int budget) {
        var matcher = separator.matcher(text);
        Cut last = null;
        while (matcher.find() && matcher.start() <= budget) {
            if (matcher.start() > 0) {
                last = new Cut(matcher.start(), matcher.end());
            }
        }
        return Optional.ofNullable(last);
    }
}
