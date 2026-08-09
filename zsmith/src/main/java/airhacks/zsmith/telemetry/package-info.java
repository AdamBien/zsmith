/// Reads the JDK Flight Recorder events zsmith emits and folds them, by run id, into what
/// each chat loop cost and where it went wrong.
///
/// The stream is an oracle, not a corpus. It answers which run was worse — more turns, more
/// retries, more tokens, a tool failing the same way every time — and it answers that well
/// enough to compare a prompt against its replacement. It does not answer what the prompt
/// should have said instead, and no amount of added telemetry would: what happened is not the
/// counterfactual, and the events carry no content by design (see `transcripts`).
///
/// What it is good for is supplying evidence a human would otherwise have to remember. A run
/// singled out here has a run id, and that id leads to the turn, the tool call, and the
/// transcript — which is exactly the `trigger` an improvement report has to carry.
///
/// Borrowed from streaming rather than from profiling: JFR gives ordered, replayable, schema'd
/// records with a decoupled consumer, and withholds offsets, acknowledgement, and at-least-once
/// delivery. Good enough to notice something; not the place to keep the only copy of it.
package airhacks.zsmith.telemetry;
