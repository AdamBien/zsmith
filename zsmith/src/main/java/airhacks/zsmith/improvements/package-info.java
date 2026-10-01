/// # Improvements
/// > Let an agent report where its own instructions failed it — the gap and the input that exposed it — as a backlog a human reads and applies.
///
/// ## Boundary
/// <!-- reporting -->
/// - `report-improvement` — record a gap in an agent's prompt, skill or tool description with the input that exposed it, answering whether it was new
///
/// <!-- reading -->
/// - `list-improvements` — return every reported gap, oldest first
///
/// <!-- removing -->
/// - `clear-improvements` — drop every report
///
/// <!-- scoping -->
/// - `open-agent-improvements` — open the backlog kept in a named agent's own database
///
/// ## Requirements
///
/// ### R1: Report a gap
/// - R1.1 — When an artifact kind, an observation and a trigger are supplied, the BC shall keep the report durably and answer that it was recorded.
/// - R1.2 — If the observation or the trigger is absent or blank, then the BC shall reject the report. _(why: what the instruction failed to say, and the input that exposed it, are what only the agent in that turn can supply; without them a report is a journal entry — D2)_
/// - R1.3 — If the artifact kind is absent or is none of prompt, skill and tool, then the BC shall reject the report.
/// - R1.4 — The BC shall accept the artifact kind regardless of case.
/// - R1.5 — If a prompt report names no artifact, then the BC shall record it under the name `system`.
/// - R1.6 — If a skill or tool report names no artifact, then the BC shall reject the report. _(why: `system` names the system prompt, so a defaulted name points the reader at the wrong artifact)_
/// - R1.7 — Where a suggestion is supplied, the BC shall keep it with the report; otherwise the BC shall give the suggestion back as empty. _(why: the rewrite is the reader's job — D2)_
/// - R1.8 — When a report is made without a time, the BC shall record the time it was made.
/// - R1.9 — When distinct reports are made within the same second, the BC shall keep each of them.
///
/// ### R2: Report each gap once
/// - R2.1 — If a report with the same artifact kind, name and observation is already kept, then the BC shall keep the stored one unchanged and answer that nothing was recorded. _(why: an agent hitting the same missing instruction in every turn would bury the backlog in copies)_
/// - R2.2 — The BC shall decide sameness by the artifact kind, the name and the exact observation text alone, disregarding trigger, suggestion and time.
/// - R2.3 — The BC shall apply the sameness check to reports kept in earlier sessions as well.
///
/// ### R3: Read the backlog
/// - R3.1 — The BC shall return every kept report, oldest first, with its artifact kind, name, observation, trigger, suggestion and time.
/// - R3.2 — The BC shall return a kept report in a later session.
/// - R3.3 — If a kept report cannot be read, then the BC shall skip it and return the rest. _(why: one hand-edited page must cost a single report, not the backlog)_
///
/// ### R4: Keep the backlog beside the agent
/// - R4.1 — When a backlog is opened for a named agent, the BC shall keep its reports in that agent's own database, alongside its memories.
/// - R4.2 — The BC shall keep the reports as a table browsable without this component. _(why: the backlog is a page to read rather than a file to grep)_
///
/// ### R5: Clear the backlog
/// - R5.1 — When clearing is requested, the BC shall drop every report from the run and from its database.
///
/// ### R6: Answer the reporting model
/// - R6.1 — When a model reports a gap, the BC shall answer whether the report was recorded, was already reported, or was refused together with the reason. _(why: a model that cannot tell a refusal from a record cannot correct its report)_
///
/// ## Entities
/// - Improvement, ArtifactKind
///
/// ## Decisions
/// - D1 — A human reads and applies the reports; nothing reported feeds back into the agent, in this session or a later one. _(why: an agent editing its own prompt has no oversight, and a bad edit changes the behaviour that would justify the next one; rejected: applying reported suggestions to the prompt, skill or tool description automatically)_
/// - D2 — A report is an incident — the gap and the input that exposed it — and a suggested rewrite is optional. _(why: an agent is a good witness and a poor designer of its own prompt; it never sees the counterfactual of a different instruction; rejected: requiring a rewrite with every report)_
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - Applying a report to a prompt, skill or tool description — D1
/// - Offering the report to a model and keeping it out of the opening turn — `agent`
/// - Deciding what is worth reporting — the model's judgement, steered by the tool description
/// - Merging reworded reports of one gap — sameness is exact, the reader merges
/// - Resolving, closing or removing a single report — clearing is all-or-nothing
/// - How a report is written to its page and rendered — `htmldb`
package airhacks.zsmith.improvements;