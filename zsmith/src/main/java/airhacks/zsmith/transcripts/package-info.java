/// Stores what a chat loop said, keyed by the run id its JFR events carry — the payload half
/// of a claim check whose envelope is the event stream.
///
/// The split is deliberate. JFR string fields live in a per-chunk constant pool, so verbatim
/// prompts and tool inputs would neither deduplicate nor stay small, and under size or age
/// retention they would evict the cheap telemetry that made the stream worth recording. A
/// `.jfr` file is also an artifact people hand around, with no redaction stage between commit
/// and reader. So the events carry the key and enough categorical detail to find an
/// interesting run, and the conversation itself stays here, in the agent's own database.
///
/// It also settles which copy is authoritative: JFR is at-most-once and drops under pressure,
/// this is a file. The stream is an index over the store, not the record of last resort.
package airhacks.zsmith.transcripts;
