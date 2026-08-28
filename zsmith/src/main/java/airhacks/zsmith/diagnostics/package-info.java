/// # Diagnostics
/// > Read a finished recording and say what was avoidable about the runs in it.
///
/// ## Boundary
/// - `diagnose-recording` — answer every finding in a recording, top-level runs before the runs they delegated to
/// - `report-findings` — answer the same findings as text, grouped by run
///
/// ## Requirements
/// ### R1: Judge a run from its recording
/// - R1.1 — When a recording is diagnosed, the BC shall answer one set of findings per run identifier the recording carries. _(why: a delegated sub-agent is its own run; a verdict spanning a parent and its children is a verdict on neither)_
/// - R1.2 — The BC shall report a run that was checked and healthy as a passing finding rather than as silence. _(why: an empty report cannot be told apart from an unchecked one)_
/// - R1.3 — The BC shall carry, with every finding, the measured values the verdict rests on. _(why: a finding that cannot be argued with can only be believed, and every one of these is arithmetic that the reader can redo)_
/// - R1.4 — If a recorded event omits a field a rule draws on, then the BC shall read that field as zero rather than fail the diagnosis. _(why: a recording outlives the code that wrote it)_
/// - R1.5 — If an event carries no run identifier, then the BC shall exclude it from every finding. _(why: it happened outside any chat loop and belongs to no run)_
///
/// ### R2: Name what a run paid for
/// - R2.1 — When a call of a run read nothing from cache and wrote a significant prefix, and it was not that run's first call, then the BC shall report the prefix as re-created at write price. _(why: the same context at cache-write price instead of cache-read price is the largest avoidable cost a conversational run has)_
/// - R2.2 — When a run's own consecutive calls are spaced further apart than the prompt cache lives, the BC shall report the stretch together with the tool that filled it, where one did. _(why: the gap is the cause and the re-created prefix the effect, so naming only the effect leaves nothing to act on — and a question nobody answered and a sub-agent still working are the same gap and not the same problem)_
/// - R2.3 — The BC shall not report a run's first call as a re-created prefix. _(why: a first call reads nothing from cache by definition — this is the only thing separating a real expiry from a sub-agent starting cold, and a delegating run has one of those per delegation)_
/// - R2.4 — The BC shall report, per tool, the bytes that tool left in the conversation across the whole run, dearest first, and shall judge it by that carry rather than by the size of any one result. _(why: a result is re-sent on every turn after the one that fetched it, so its cost is size times turns carried — and the two orders disagree, a single 67 KB read costing less than three 27 KB recalls in a longer run; reporting per call would also read as three problems where there is one thing to fix)_
/// - R2.5 — The BC shall report how many tool calls a run got out of each turn that asked for any, and shall not judge this by how the tools were executed. _(why: an extra turn is a whole round trip that re-sends the conversation, whereas whether two tools ran concurrently is a property the tool declares — a run using only sequential tools has serialized nothing)_
/// - R2.6 — The BC shall report a run's retries and its tool failures, keyed by what went wrong when there were any.
/// - R2.7 — The BC shall report a run that never reached a terminal turn as incomplete.
/// - R2.8 — The BC shall report a delegated run's cost against that run and shall not add it to the run that dispatched it. _(why: folding a child's spend into its parent would leave neither number the cost of anything)_
///
/// ## Entities
/// - Finding — one judgement about a run, with the measurements it rests on
/// - Timeline — one run's calls and tool calls in the order they happened
/// - Call — one LLM call, kept in sequence
/// - ToolCall — one tool execution, reduced to what decides whether it was expensive and what it held up
/// - CarriedContext — what one tool cost a run by what it left in the conversation
///
/// ## Decisions
/// - D1 — Every rule is arithmetic over recorded fields; no model reads the recording. _(why: the findings that matter are sums and differences over fields the stream already carries, and a model asked to find them in a 7 MB log would be guessing at what it could have counted; rejected: an agent reading logs and recordings directly, which pays model tokens to do subtraction and cannot be tested for the same answer twice)_
/// - D2 — Sequence is folded here rather than added to `telemetry`'s report. _(why: that BC's report is a sum and its spec puts the counterfactual out of scope on the record; a sum cannot tell four sub-agents starting cold apart from one prefix expiring mid-conversation, and widening the report with thresholds and verdicts would contradict a decision it has already written down)_
/// - D3 — The recording is read twice, once through `telemetry`'s public replay and once for the timeline. _(why: piggybacking a second consumer onto that fold would buy one pass over a bounded file at the price of a BC boundary)_
/// - D4 — Only finished recordings are diagnosed. _(why: a run still in flight has not spent what it is going to, so a verdict on it would change every time it was asked for)_
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - Why a run did what it did — that needs the conversation, which lives in `transcripts` under the same run identifier, and reading it is judgement rather than arithmetic
/// - Emitting the events, and folding them into per-run cost — `telemetry`
/// - Turning findings into money — the same reason `telemetry` does not price tokens
/// - Rewriting the system prompt, the skill or the tool description a finding implicates — `improvements` collects instruction gaps, and mechanical findings landing in that backlog would blur what it is for
/// - Diagnosing a run while it is still running
/// - Deciding what a run should have done instead in any sense a threshold cannot express
package airhacks.zsmith.diagnostics;
