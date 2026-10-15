package airhacks.zsmith.chunking.control;

import static airhacks.zsmith.chunking.Requirement.Rn.*;

import airhacks.zsmith.chunking.Requirement;
import airhacks.zsmith.chunking.entity.Chunk;
import airhacks.zsmith.chunking.entity.ChunkProcessedEvent;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.llm.entity.LLMResponse;
import airhacks.zsmith.logging.control.Log;

/// Runs a text too large for a model's context through it anyway: split into chunks that fit,
/// one instruction applied to each chunk in turn, the replies joined back into one text on the
/// separators the cuts fell on.
public record Chunking(Model model, int budget, float temperature) {

    public static final String BUDGET_KEY = "chunking.budget";
    public static final String TEMPERATURE_KEY = "chunking.temperature";
    public static final int DEFAULT_BUDGET = 8_000;
    public static final float DEFAULT_TEMPERATURE = 0.1f;

    /// The model `llm` is configured for, the budget from `chunking.budget` and the temperature
    /// from `chunking.temperature`.
    public static Chunking configured() {
        return new Chunking(Model.CONFIGURED,
                ZCfg.integer(BUDGET_KEY, DEFAULT_BUDGET),
                parseFloat(ZCfg.string(TEMPERATURE_KEY, null), DEFAULT_TEMPERATURE));
    }

    /// `process-text` with the configured budget.
    public String processText(String instruction, String text) {
        rejectBlank(instruction);
        return process(instruction, text, this.budget);
    }

    /// `process-text` — apply an instruction to a text chunk by chunk and answer the joined result.
    @Requirement({ R1_1, R1_2, R1_3, R1_4, R1_5, R1_6, R1_7, R1_8, R2_1, R2_2, R2_3, R2_4, R2_5, R3_1, R3_2,
            R4_1, R4_2, R4_3, R4_4, R4_5, R4_6 })
    public String processText(String instruction, String text, int budget) {
        rejectBlank(instruction);
        if (budget <= 0) {
            throw new IllegalArgumentException("the budget has to be a positive number of characters, not " + budget);
        }
        return process(instruction, text, budget);
    }

    String process(String instruction, String text, int budget) {
        if (text == null || text.isBlank()) {
            return text;
        }
        var chunks = Splitter.split(text, budget);
        var joined = new StringBuilder();
        for (var chunk : chunks) {
            joined.append(reply(instruction, chunk, chunks.size())).append(chunk.separator());
        }
        return joined.toString();
    }

    /// The reply trimmed, or the blank chunk itself, which no model is asked about.
    String reply(String instruction, Chunk chunk, int count) {
        if (chunk.blank()) {
            processed(chunk, count, chunk.text().length(), ChunkProcessedEvent.KEPT_BLANK);
            return chunk.text();
        }
        var position = chunk.position();
        LLMResponse response;
        try {
            response = this.model.reply(instruction, chunk.text(), this.temperature);
        } catch (RuntimeException e) {
            processed(chunk, count, 0, ChunkProcessedEvent.FAILED);
            throw new IllegalStateException("the model failed on chunk %d of %d: %s"
                    .formatted(position, count, e.getMessage()), e);
        }
        if (Model.CUT_OFF.equals(response.stopReason())) {
            processed(chunk, count, 0, ChunkProcessedEvent.TRUNCATED);
            throw new IllegalStateException("chunk %d of %d was cut off at the model's output limit"
                    .formatted(position, count));
        }
        var reply = response.text().strip();
        if (reply.isEmpty()) {
            processed(chunk, count, 0, ChunkProcessedEvent.EMPTY_REPLY);
            throw new IllegalStateException("the model answered chunk %d of %d with an empty reply"
                    .formatted(position, count));
        }
        processed(chunk, count, reply.length(), ChunkProcessedEvent.PROCESSED);
        return reply;
    }

    static void processed(Chunk chunk, int count, int outputCharacters, String outcome) {
        Log.agent("chunk %d/%d %s".formatted(chunk.position(), count, outcome));
        ChunkProcessedEvent.record(chunk, count, outputCharacters, outcome);
    }

    static void rejectBlank(String instruction) {
        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("an instruction is required to process a text");
        }
    }

    static float parseFloat(String value, float fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
