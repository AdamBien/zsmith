/// # Correlation
/// > Give everything recorded during one chat loop a shared identity — run, turn and depth — so the events of a run can be joined rather than guessed at.
///
/// ## Boundary
/// <!-- deriving -->
/// - `start-run` — answer the correlation of a fresh run, positioned at its first turn
/// - `advance-turn` — answer the same run positioned at a given turn
/// - `descend-level` — answer the correlation a delegated run starts under, one level further down
///
/// <!-- carrying -->
/// - `run-under` — make a correlation the current one for a piece of work and hand back the work's result
/// - `read-current` — answer the correlation the calling work runs under
///
/// ## Requirements
///
/// ### R1: Start a run
/// - R1.1 — When a run is started, the BC shall answer a known correlation positioned at turn 0.
/// - R1.2 — The BC shall give every started run a run id no other run carries. _(why: two agents running at once make a time window useless as a join key)_
/// - R1.3 — When a run is started under a correlation, the BC shall position it at the depth of that correlation.
/// - R1.4 — When a run is started under a known correlation, the BC shall give it a run id of its own rather than the one it started under. _(why: every chat loop is one run; a delegated loop is linked to the run that delegated, not merged into it)_
///
/// ### R2: Advance to a turn
/// - R2.1 — When a turn is given, the BC shall answer a correlation with the same run id and depth, positioned at that turn.
/// - R2.2 — If the turn is negative, then the BC shall reject it. _(why: turns count from 0, and the negative position is how the unknown correlation is told apart)_
///
/// ### R3: Descend a level
/// - R3.1 — When a level is descended, the BC shall answer a correlation one level deeper with the same run id and turn. _(why: the delegated run reads the run that delegated to it from the correlation it starts under)_
/// - R3.2 — If the correlation descended from is unknown, then the BC shall answer an unknown correlation one level deeper.
///
/// ### R4: Run work under a correlation
/// - R4.1 — While work runs under a correlation, the BC shall answer that correlation as the current one.
/// - R4.2 — When the work completes, the BC shall hand back its result.
/// - R4.3 — When work run under a correlation completes, the BC shall answer as current the correlation that was current before it.
/// - R4.4 — If the work fails, then the BC shall pass the failure on and answer as current the correlation that was current before it.
///
/// ### R5: Read the current correlation
/// - R5.1 — While no work runs under a correlation, the BC shall answer the unknown correlation. _(why: a store initialising or a tool invoked directly still records events, and they must not look like part of a run)_
/// - R5.2 — The BC shall give the unknown correlation a blank run id, turn -1 and depth 0, and answer that it is not known.
/// - R5.3 — If a correlation carries no run id, then the BC shall treat its run id as blank and answer that it is not known.
///
/// ## Entities
/// - Correlation
///
/// ## Decisions
/// - D1 — Correlation is a component of its own. _(why: the LLM transports read it and `agent` already declares LLM transport out of scope, so the dependency has to point the other way; rejected: keeping it part of `agent`)_
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - Carrying the current correlation onto work handed off to run elsewhere — the caller passes it along and runs that work under it
/// - Recording which run delegated to which — `agent`, from the correlation a run starts under
/// - Limiting how deep delegation may go — `agent`
/// - What actually happened in a run — the correlation is a key, not a payload; the content stays in `memory` and `transcripts`
/// - The events themselves and what each carries besides the key — the component that emits them
package airhacks.zsmith.correlation;
