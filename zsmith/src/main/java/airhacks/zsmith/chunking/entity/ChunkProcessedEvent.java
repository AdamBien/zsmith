package airhacks.zsmith.chunking.entity;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import airhacks.zsmith.Concern;
import static airhacks.zsmith.Concern.Kind.OBSERVABILITY;

@Concern(OBSERVABILITY)
@Name(ChunkProcessedEvent.NAME)
@Label("Chunk Processed")
@Category({"zsmith", "chunking"})
@Description("One chunk of a split text run through the model")
public class ChunkProcessedEvent extends Event {

    /// The registered event name, so consumers of the stream name it through the
    /// event that emits it rather than repeating a literal.
    public static final String NAME = "airhacks.zsmith.chunking.ChunkProcessed";

    public static final String PROCESSED = "processed";
    public static final String KEPT_BLANK = "kept-blank";
    public static final String FAILED = "failed";
    public static final String TRUNCATED = "truncated";
    public static final String EMPTY_REPLY = "empty-reply";

    @Label("Position")
    @Description("1-based position of the chunk in the split text")
    public int position;

    @Label("Chunk Count")
    public int count;

    @Label("Input Characters")
    public int inputCharacters;

    @Label("Output Characters")
    @Description("Characters of the reply as joined, zero when the chunk failed")
    public int outputCharacters;

    @Label("Outcome")
    @Description("processed, kept-blank, failed, truncated or empty-reply")
    public String outcome;

    public static void record(Chunk chunk, int count, int outputCharacters, String outcome) {
        var event = new ChunkProcessedEvent();
        event.position = chunk.position();
        event.count = count;
        event.inputCharacters = chunk.text().length();
        event.outputCharacters = outputCharacters;
        event.outcome = outcome;
        event.commit();
    }
}
