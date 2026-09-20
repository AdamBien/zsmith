/// # Episodic Memory
/// > Keep what stays worth knowing after a session ends, and give it back — by recency, by type, or by what a question is about.
///
/// ## Boundary
/// <!-- keeping -->
/// - `store-memory` — keep a durable fact under its type, answering whether it was new
/// - `forget-memories` — drop what this agent learned
///
/// <!-- giving back -->
/// - `recall-recent-memories` — return the most recently kept memories
/// - `recall-memories-by-type` — return the memories carrying one type
/// - `search-memories` — return the memories a query is about, most relevant first
/// - `offer-memory-catalog` — publish a capped selection for injection into an agent's instructions
///
/// <!-- scoping -->
/// - `open-agent-memory` — open a memory owned by a named agent that shares what is known about the user
///
/// ## Requirements
///
/// ### R1: Keep a fact
/// - R1.1 — When a fact and its type are supplied, the BC shall keep them durably and report the fact as newly remembered.
/// - R1.2 — If a fact carrying the same statement and type is already kept, then the BC shall keep the stored one unchanged and report that nothing was kept. _(why: a model restating one fact across turns would otherwise spend the recall caps on copies of it)_
/// - R1.3 — If the supplied fact carries no statement, then the BC shall reject it.
/// - R1.4 — When a fact is kept without a time, the BC shall record the time it was kept.
/// - R1.5 — The BC shall return a kept fact in a later session. _(why: a memory that does not survive the run it was learned in is a transcript, not a memory)_
///
/// ### R2: Recall by recency
/// - R2.1 — When a count is requested, the BC shall return at most that many most recently kept memories, oldest first.
/// - R2.2 — If the requested count is zero or negative, then the BC shall return no memory.
/// - R2.3 — If fewer memories are kept than requested, then the BC shall return every kept memory.
///
/// ### R3: Recall by type
/// - R3.1 — When a type is supplied, the BC shall return every memory carrying it, oldest first.
/// - R3.2 — If no memory carries the supplied type, then the BC shall report that none was found.
///
/// ### R4: Find the memories a query is about
/// - R4.1 — When a query is supplied, the BC shall return the memories relevant to it, most relevant first.
/// - R4.2 — When two memories are alike but for the query terms they carry, the BC shall rank the one carrying more of them first.
/// - R4.3 — The BC shall weigh a query term carried by few memories above one carried by many. _(why: a term nearly every memory carries separates nothing, so it must not decide the ranking)_
/// - R4.4 — The BC shall let each further occurrence of a term within one memory raise that memory's rank by less than the occurrence before it. _(why: a memory repeating a word is not proportionally more about it, and unbounded counting lets one verbose memory win every query)_
/// - R4.5 — The BC shall match a query term against any word beginning with it, disregarding case. _(why: a remembered fact and the later question about it commonly differ only by an inflection — build against builds — which exact matching misses)_
/// - R4.6 — When memories are equally relevant, the BC shall return the more recently kept one first. _(why: an arbitrary tie makes a result unreproducible, and recency is the tie-break the rest of this BC already uses)_
/// - R4.7 — Where a count is supplied, the BC shall return at most that many memories.
/// - R4.8 — Where a type is supplied, the BC shall rank only the memories carrying it.
/// - R4.9 — If no kept memory carries any term of the query, then the BC shall report that none was found. _(why: returning recent memories instead leaves the caller unable to tell a topical hit from filler, and acting on filler is worse than knowing nothing)_
/// - R4.10 — If the query carries no searchable term, then the BC shall report the query as empty rather than return memories.
///
/// ### R5: Separate what is known about the user from what an agent learned
/// - R5.1 — When a memory about the user is kept by an agent-owned memory, the BC shall write it to the shared scope. _(why: every agent is talking to the same person)_
/// - R5.2 — When a memory of any other type is kept, the BC shall write it to the scope owned by the agent that learned it.
/// - R5.3 — The BC shall read the scopes it holds as one, so recall, search and the catalog span them.
/// - R5.4 — While reading a shared scope it does not own, the BC shall admit only the memories about the user. _(why: another agent's project notes are that agent's context, not this one's)_
///
/// ### R6: Publish memories for an agent's instructions
/// - R6.1 — The BC shall publish kept memories as a catalog carrying each memory's date and type, oldest first.
/// - R6.2 — The BC shall cap the catalog at a configured count per type and a configured total.
/// - R6.3 — The BC shall shorten a memory exceeding the publishable length.
/// - R6.4 — If no memory is kept, or either cap is zero or negative, then the BC shall publish nothing. _(why: an empty section still costs every request the tokens to carry it)_
/// - R6.5 — The BC shall present the catalog as background context rather than as instruction. _(why: an injected memory is a hint from an earlier session, and a model reading it as a command acts on stale context)_
///
/// ### R7: Forget what this agent learned
/// - R7.1 — When forgetting is requested, the BC shall drop the memories this agent learned, from the run and from its database.
/// - R7.2 — While what is known about the user lives in a scope this agent does not own, the BC shall keep it. _(why: the other agents share it, so one agent forgetting must not blind the rest)_
///
/// ### R8: Survive a damaged or outdated store
/// - R8.1 — If a kept memory cannot be read, then the BC shall skip it and return the rest. _(why: one hand-edited or truncated record must cost a single memory, not the whole store — the reason memories are kept as separate records)_
/// - R8.2 — When a store written in the superseded single-file format is opened, the BC shall import its memories once and set the old file aside.
/// - R8.3 — When importing, the BC shall collapse repeated statements into one.
/// - R8.4 — When importing, the BC shall move what an agent learned about the user into the shared scope. _(why: that is the scope it would be written to today)_
/// - R8.5 — If the store cannot be read at all, then the BC shall report the failure and continue with no memory. _(why: unreadable memory must never be what ends a run)_
///
/// ### R9: Report what memory access costs
/// - R9.1 — When memories are loaded or kept, the BC shall emit an event carrying the run it happened in, the operation, the number of memories held, the payload size and the outcome.
/// - R9.2 — If an access fails, then the BC shall report the failure as that event's outcome. _(why: a load that silently returned nothing is indistinguishable from an empty memory)_
///
/// ## Entities
/// - Episode, MemoryType, MemoryAccessEvent
///
/// ## Decisions
/// - D1 — Finding by topic is its own boundary operation, beside recall by recency and by type. _(why: browsing what is recent and finding what a question is about are different promises, separately testable and separately described to a model; rejected: an optional query parameter on recall, which puts two behaviours under one contract and one description)_
/// - D2 — The ranking is lexical and lives in the control layer; the spec declares only the properties a caller may rely on. _(why: rarity, saturation, prefix matching and a stable tie-break outlive whichever scorer produces them; rejected: naming the scoring function and its parameters in the contract, which pins an implementation into a spec that promises only what)_
/// - D3 — Query terms match by case-insensitive prefix. _(why: the cheapest morphology that survives the common inflection without carrying a language-specific stemmer; rejected: whole-token equality, which misses build against builds, and substring matching anywhere in a word, which makes a three-letter query match nearly every memory)_
/// - D4 — Relevance serves `search-memories` only; the injected catalog stays capped by recency. _(why: the catalog is built before the turn's question is known, and re-ranking it would change the instructions every existing agent already ships with; rejected: ranking the catalog against the turn in progress)_
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - Ranking the injected catalog against the turn in progress — D4
/// - Semantic similarity and synonym expansion — ranking is lexical, so a query and the memory it should find must share a word
/// - A persisted or incrementally maintained search index — the memories are already held for the run and ranked there
/// - Editing, retyping or dropping a single memory — memory is append-only, and forgetting is all-or-nothing
/// - Deciding what is worth remembering — the agent's judgement, steered by the tool descriptions, not by this BC
/// - Searching past conversations — `transcripts`
/// - Recording, folding and replaying the emitted events — `telemetry`
/// - Where a memory is physically written and how it is rendered — `htmldb`
package airhacks.zsmith.episodicmemory;
