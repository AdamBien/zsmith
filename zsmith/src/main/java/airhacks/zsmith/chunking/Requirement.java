package airhacks.zsmith.chunking;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Generated from the capability spec in [airhacks.zsmith.chunking] — do not edit.
/// Marks the boundary method or test that realizes the given requirement statements.
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Requirement {

    /// One constant per statement id in the spec's `## Requirements`.
    enum Rn {
        /// While no budget is given, the BC shall cut the text into chunks of at most the configured budget — 8,000 characters unless configured.
        R1_1("R1.1", "While no budget is given, the BC shall cut the text into chunks of at most the configured budget — 8,000 characters unless configured."),
        /// Where a request gives a budget, the BC shall cut the text into chunks of at most that many characters.
        R1_2("R1.2", "Where a request gives a budget, the BC shall cut the text into chunks of at most that many characters."),
        /// When the text fits the budget, the BC shall process it as a single chunk.
        R1_3("R1.3", "When the text fits the budget, the BC shall process it as a single chunk."),
        /// When the text exceeds the budget, the BC shall cut each chunk at the last paragraph break within the budget.
        R1_4("R1.4", "When the text exceeds the budget, the BC shall cut each chunk at the last paragraph break within the budget."),
        /// If no paragraph break lies within the budget, then the BC shall cut at the last sentence end within the budget.
        R1_5("R1.5", "If no paragraph break lies within the budget, then the BC shall cut at the last sentence end within the budget."),
        /// If no sentence end lies within the budget, then the BC shall cut at the last whitespace within the budget.
        R1_6("R1.6", "If no sentence end lies within the budget, then the BC shall cut at the last whitespace within the budget."),
        /// If no whitespace lies within the budget, then the BC shall cut at exactly the budget.
        R1_7("R1.7", "If no whitespace lies within the budget, then the BC shall cut at exactly the budget."),
        /// The BC shall keep the separator a cut falls on out of both chunks.
        R1_8("R1.8", "The BC shall keep the separator a cut falls on out of both chunks."),

        /// When a text is split, the BC shall send the chunks in order, one at a time, each as the only message of a fresh conversation, with the instruction as the system prompt and no tools offered.
        R2_1("R2.1", "When a text is split, the BC shall send the chunks in order, one at a time, each as the only message of a fresh conversation, with the instruction as the system prompt and no tools offered."),
        /// The BC shall send every chunk at the configured temperature.
        R2_2("R2.2", "The BC shall send every chunk at the configured temperature."),
        /// If a chunk is blank, then the BC shall keep it unchanged without calling the model.
        R2_3("R2.3", "If a chunk is blank, then the BC shall keep it unchanged without calling the model."),
        /// When a chunk is processed, the BC shall show its position and the chunk count as progress.
        R2_4("R2.4", "When a chunk is processed, the BC shall show its position and the chunk count as progress."),
        /// When a chunk is processed, the BC shall record an event with its position, the chunk count, its input and output characters and the outcome.
        R2_5("R2.5", "When a chunk is processed, the BC shall record an event with its position, the chunk count, its input and output characters and the outcome."),

        /// When every chunk is processed, the BC shall answer the replies in the order of their chunks, each trimmed of leading and trailing whitespace, joined by the separator each cut fell on.
        R3_1("R3.1", "When every chunk is processed, the BC shall answer the replies in the order of their chunks, each trimmed of leading and trailing whitespace, joined by the separator each cut fell on."),
        /// Where a cut fell at the budget with no separator, the BC shall join the replies on either side of it with nothing between them.
        R3_2("R3.2", "Where a cut fell at the budget with no separator, the BC shall join the replies on either side of it with nothing between them."),

        /// If the instruction is missing or blank, then the BC shall reject the request.
        R4_1("R4.1", "If the instruction is missing or blank, then the BC shall reject the request."),
        /// If a given budget is not a positive number, then the BC shall reject the request.
        R4_2("R4.2", "If a given budget is not a positive number, then the BC shall reject the request."),
        /// If the text is missing, empty or whitespace only, then the BC shall answer it unchanged without calling the model.
        R4_3("R4.3", "If the text is missing, empty or whitespace only, then the BC shall answer it unchanged without calling the model."),
        /// If the model fails on a chunk, then the BC shall fail the whole text, naming the chunk's position and the model's reason, and send no further chunk.
        R4_4("R4.4", "If the model fails on a chunk, then the BC shall fail the whole text, naming the chunk's position and the model's reason, and send no further chunk."),
        /// If the model's reply to a chunk is cut off at the output limit, then the BC shall fail the whole text, naming the chunk's position, and send no further chunk.
        R4_5("R4.5", "If the model's reply to a chunk is cut off at the output limit, then the BC shall fail the whole text, naming the chunk's position, and send no further chunk."),
        /// If the model answers a non-blank chunk with an empty reply, then the BC shall fail the whole text, naming the chunk's position, and send no further chunk.
        R4_6("R4.6", "If the model answers a non-blank chunk with an empty reply, then the BC shall fail the whole text, naming the chunk's position, and send no further chunk.");

        private final String id;
        private final String statement;

        Rn(String id, String statement) {
            this.id = id;
            this.statement = statement;
        }

        public String statement() {
            return this.statement;
        }

        @Override
        public String toString() {
            return this.id;
        }
    }

    Rn[] value();
}
