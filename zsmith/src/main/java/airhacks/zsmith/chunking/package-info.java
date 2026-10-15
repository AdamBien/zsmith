/// # Chunking
/// > Run a text too large for a model's context through it anyway: split it into chunks that fit, process each chunk with one instruction, and join the results back into one text.
///
/// ## Boundary
/// - `process-text` — apply an instruction to a text chunk by chunk and answer the joined result
///
/// ## Requirements
///
/// ### R1: Split the text
/// - R1.1 — While no budget is given, the BC shall cut the text into chunks of at most the configured budget — 8,000 characters unless configured.
/// - R1.2 — Where a request gives a budget, the BC shall cut the text into chunks of at most that many characters.
/// - R1.3 — When the text fits the budget, the BC shall process it as a single chunk.
/// - R1.4 — When the text exceeds the budget, the BC shall cut each chunk at the last paragraph break within the budget.
/// - R1.5 — If no paragraph break lies within the budget, then the BC shall cut at the last sentence end within the budget.
/// - R1.6 — If no sentence end lies within the budget, then the BC shall cut at the last whitespace within the budget. _(why: a word cut in half is a word the model cannot clean up)_
/// - R1.7 — If no whitespace lies within the budget, then the BC shall cut at exactly the budget. _(why: a long URL or token must not make a text unprocessable)_
/// - R1.8 — The BC shall keep the separator a cut falls on out of both chunks.
///
/// ### R2: Process each chunk
/// - R2.1 — When a text is split, the BC shall send the chunks in order, one at a time, each as the only message of a fresh conversation, with the instruction as the system prompt and no tools offered. _(why: one on-device model serves every call, so concurrency buys little)_
/// - R2.2 — The BC shall send every chunk at the configured temperature.
/// - R2.3 — If a chunk is blank, then the BC shall keep it unchanged without calling the model.
/// - R2.4 — When a chunk is processed, the BC shall show its position and the chunk count as progress.
/// - R2.5 — When a chunk is processed, the BC shall record an event with its position, the chunk count, its input and output characters and the outcome.
///
/// ### R3: Join the results
/// - R3.1 — When every chunk is processed, the BC shall answer the replies in the order of their chunks, each trimmed of leading and trailing whitespace, joined by the separator each cut fell on. _(why: transcription cleanup must keep the paragraph layout of the original)_
/// - R3.2 — Where a cut fell at the budget with no separator, the BC shall join the replies on either side of it with nothing between them.
///
/// ### R4: Reject and fail
/// - R4.1 — If the instruction is missing or blank, then the BC shall reject the request.
/// - R4.2 — If a given budget is not a positive number, then the BC shall reject the request.
/// - R4.3 — If the text is missing, empty or whitespace only, then the BC shall answer it unchanged without calling the model.
/// - R4.4 — If the model fails on a chunk, then the BC shall fail the whole text, naming the chunk's position and the model's reason, and send no further chunk.
/// - R4.5 — If the model's reply to a chunk is cut off at the output limit, then the BC shall fail the whole text, naming the chunk's position, and send no further chunk.
/// - R4.6 — If the model answers a non-blank chunk with an empty reply, then the BC shall fail the whole text, naming the chunk's position, and send no further chunk. _(why: an empty reply silently deletes content)_
///
/// ## Entities
/// - Chunk, ChunkProcessedEvent
///
/// ## Decisions
/// <!-- append-only confirmed choices with rejected alternatives; rationale, not contract — no test; supersede, never edit -->
/// - D1 — Each chunk is processed without context from its neighbours. _(why: cuts at paragraph and sentence ends already bound the damage, and context eats a small budget a small model may echo back anyway; rejected: sending the preceding chunk's tail as read-only context, sending the preceding processed result)_
/// - D2 — A failed, truncated or empty chunk fails the whole text. _(why: a cleanup with a silently unprocessed or truncated piece reads as done but is not; rejected: keeping the original chunk in its place, retrying the chunk once)_
/// - D3 — The budget is measured in characters and configurable. _(why: deterministic and testable, while tokens are only ever estimated; rejected: an estimated token budget, a budget derived from the model's context size)_
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - Choosing and calling the model — `llm`, through whichever provider is configured
/// - Context carried between chunks (D1), concurrent processing, retries
/// - Deriving the budget from a model's context size or counting tokens
/// - Offering chunked processing to an agent as a tool
/// - Reading or writing files, streaming replies
package airhacks.zsmith.chunking;
