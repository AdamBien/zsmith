/// # Telemetry
/// > Answer what each chat loop cost and where it went wrong — from the recorded event stream, and from the run still in flight.
///
/// ## Boundary
/// <!-- recorded runs -->
/// - `replay-recording` — fold a finished recording into one report per run
/// - `observe-live-events` — fold this JVM's events into reports as they are flushed
/// - `stop-observing` — release a live observation
/// - `report-run-cost` — return the reports folded so far, keyed by run
///
/// <!-- the run in flight -->
/// - `tally-token-usage` — record one LLM call's token usage against a run
/// - `report-running-tokens` — return what a run has spent so far
/// - `discard-run-tally` — release a finished run's tally
///
/// ## Requirements
/// ### R1: Fold events into per-run reports
/// - R1.1 — When a recording is replayed, the BC shall fold every event it carries into one report per run identifier, and shall answer only once the recording is exhausted. _(why: a number that gets compared against another run's has to be scored from the whole file, identically on every read)_
/// - R1.2 — When live observation is started, the BC shall fold this JVM's events into reports as they are flushed.
/// - R1.3 — If an event carries no run identifier, then the BC shall exclude it from every report. _(why: it happened outside any chat loop — a skill read at store construction, a tool driven straight from a test — and belongs to no run)_
/// - R1.4 — If a recorded event omits a field a report draws on, then the BC shall fold that field as zero rather than fail the replay. _(why: a recording outlives the code that wrote it, which is most of what keeping it is worth)_
/// - R1.5 — When reports are requested during live observation, the BC shall answer a snapshot of what has arrived rather than a view onto what is still arriving.
/// - R1.6 — When a live observation is stopped, the BC shall release its subscription to the event stream.
///
/// ### R2: Report what a run cost
/// - R2.1 — The BC shall report, per run, the agent that ran it, its depth, and its counts of turns, tool calls, tool failures, sub-agent dispatches and API calls.
/// - R2.2 — The BC shall report a run's token usage with input, output, cache-read and cache-creation counts held apart. _(why: cache reads are the same context at a different price — a run whose input grows while its cache reads do not is losing the prefix, and that only shows when the two are split)_
/// - R2.3 — The BC shall report each tool failure keyed by what went wrong: the failure type when the tool threw, the refusal outcome when it never ran at all. _(why: "denied on every run" and "throws on every run" are the same finding at different stages)_
/// - R2.4 — When a tool was refused before it ran, the BC shall count it as both a call and a failure. _(why: the turn still spent a round trip on it)_
/// - R2.5 — When an API call was a repeat attempt, the BC shall count it as a retry.
/// - R2.6 — The BC shall report a run that never reached a terminal turn as incomplete. _(why: it hit the iteration limit or died mid-loop — the single most useful thing to filter a recording by)_
/// - R2.7 — The BC shall report every run under the identifier its events carry. _(why: the report says what a run cost, never why; the why lives in the transcript stored under the same key)_
///
/// ### R3: Tally the run in flight
/// - R3.1 — When an LLM call reports its token usage for a run, the BC shall add that usage to the run's running tally.
/// - R3.2 — When a run's running tally is requested, the BC shall answer the usage accumulated for that run alone. _(why: a delegated run is its own run — folding a child's usage into its parent would leave neither number the cost of anything)_
/// - R3.3 — When no usage has been tallied for a run, the BC shall answer a zero tally. _(why: the loop displays the tally from its first turn, before any call has returned)_
/// - R3.4 — If reported usage carries no run identifier, then the BC shall discard it.
/// - R3.5 — When several calls of one run report usage concurrently, the BC shall accumulate every one of them. _(why: a run reaches the transports from more than one thread once tools run in parallel)_
/// - R3.6 — When a run's tally is discarded, the BC shall release it. _(why: a long-lived process would otherwise retain one tally for every run it has ever executed)_
/// - R3.7 — The BC shall report a tally's total as the sum of its input, output, cache-read and cache-creation counts.
///
/// ## Entities
/// - RunReport — what one run cost and where it went wrong
/// - TokenUsage — one run's spend, input, output, cache-read and cache-creation held apart
///
/// ## Decisions
/// - D1 — Run accounting is built on JDK Flight Recorder. _(why: ordered, replayable, schema'd records with a decoupled consumer, costing nothing while unrecorded; rejected: a bespoke log, which gives neither schema nor replay, and treating the stream as the only copy — JFR withholds offsets, acknowledgement and at-least-once delivery, so it is good enough to notice something and never the place to keep the only copy of it)_
/// - D2 — The running tally is reported in-process at the LLM call site rather than read from the live event stream. _(why: live observation delivers nothing committed before the stream starts and puts a flush interval between a commit and its delivery, so a per-turn display would lag or miss turns; rejected: driving the tally from live observation, which would reuse the existing folding but cannot be exact for a run still in flight)_
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - What a run should have done instead — the stream is what happened, never the counterfactual, and no amount of added telemetry would change that
/// - The content of a run: prompts, tool inputs, error messages (`transcripts`, found by the same run identifier)
/// - Emitting the events — every BC emits its own (`agent`, `tools`, `subagent`, `skills`, `episodicmemory`)
/// - Deciding which LLM calls report usage — a transport reports what it received
/// - Turning token counts into money
/// - Displaying a run's progress while it runs (`agent`)
/// - Persisting a report or a tally beyond the process
package airhacks.zsmith.telemetry;
