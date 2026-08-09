/// # Agent
/// > Run an LLM-driven reasoning loop: converse, execute tools, and return the final answer.
///
/// ## Boundary
/// - `create-agent` — construct a named agent with a system prompt and configured defaults
/// - `equip-tools` — register tool handlers the LLM may invoke
/// - `equip-skills` — attach a skill catalog, loadable on demand
/// - `equip-episodic-memory` — attach persistent memories with store and recall
/// - `delegate-to-sub-agent` — expose a child agent as a tool
/// - `serve-http` — expose chat over per-session HTTP
/// - `serve-agentcore` — expose per-session chat through the AWS AgentCore runtime contract
/// - `chat` — run the reasoning loop for a user message and return the final answer
/// - `act` — run the reasoning loop without a user message
/// - `clear-memory` — discard the conversation history
/// - `report-version` — expose the framework version
///
/// ## Requirements
/// ### R1: Create an agent
/// - R1.1 — When an agent is created, the BC shall resolve name, system prompt, iteration limit, and temperature from the provided values, falling back to configuration defaults.
/// - R1.2 — Where a persisted system prompt exists for the agent name, the BC shall prefer it over the provided prompt. _(why: prompts are engineered and versioned outside the code)_
/// - R1.3 — When an agent is created with a name, the BC shall load that agent's named configuration.
///
/// ### R2: Converse
/// - R2.1 — When a user message is submitted, the BC shall record it in conversation memory and invoke the LLM with the system prompt, the conversation, and all registered tool definitions.
/// - R2.2 — While the LLM requests tool use, the BC shall execute the requested tools, record the results in conversation memory, and re-invoke the LLM.
/// - R2.3 — When the LLM responds without requesting tools, the BC shall record and return the assistant text as the final answer.
/// - R2.4 — If the iteration limit is reached before a final answer, then the BC shall stop the loop and report that the limit was reached.
/// - R2.5 — If the conversation loop fails, then the BC shall return a summarized error instead of propagating the failure. _(why: an agent embedded in a script must degrade to text, not stack traces)_
/// - R2.6 — If a chat is requested without a message, then the BC shall reject the request.
/// - R2.7 — When acting without a user message, the BC shall run the conversation with a generic go trigger.
/// - R2.8 — The BC shall emit an observable turn event for every loop iteration.
/// - R2.9 — The BC shall give every turn of one conversation the same run identifier, and where the conversation was started by a delegating run, shall record that run and its depth on each turn. _(why: the events of a run are only joinable if they share a key, and a delegated run is otherwise orphaned)_
/// - R2.10 — Where transcript storage is enabled, the BC shall store the conversation under the run identifier when the loop ends. _(why: the events carry the key, not the content — the content has to be findable by the same key)_
///
/// ### R3: Execute tools
/// - R3.1 — If a requested tool is not registered, then the BC shall answer the request with an error result.
/// - R3.2 — If a tool's permission resolves to deny, then the BC shall refuse execution with an error result.
/// - R3.3 — While a tool's permission resolves to confirm, when the tool is requested, the BC shall ask the user before executing and persist an always or never answer.
/// - R3.4 — If a tool execution fails, then the BC shall convert the failure into an error result. _(why: errors must flow back to the LLM, not kill the loop)_
/// - R3.5 — When several parallel-capable tools are requested in one turn, the BC shall execute them concurrently and the remaining tools sequentially.
/// - R3.6 — When a tool is executed, the BC shall record the run, the iteration, and the requesting tool-use identifier on the invocation event, and the failure type when the tool throws. _(why: an outcome of error says a run went wrong, not in what way)_
/// - R3.7 — When a tool is executed concurrently, the BC shall make the run identifier available to what the tool itself invokes. _(why: a scoped binding does not cross an executor, so nested memory, LLM and sub-agent events would leave the run)_
///
/// ### R4: Equip capabilities
/// - R4.1 — When a tool is registered, the BC shall expose its definition to the LLM on every invocation.
/// - R4.2 — When skills are attached, the BC shall append the skill catalog to the system prompt and register a skill-loading tool.
/// - R4.3 — When episodic memory is attached, the BC shall append the memory catalog to the system prompt and register memory store and recall tools.
/// - R4.4 — If an attached skill or memory catalog is empty, then the BC shall leave the system prompt unchanged.
/// - R4.5 — When a sub-agent is attached, the BC shall expose the child agent as a tool.
///
/// ### R5: Serve sessions
/// - R5.1 — When serving over HTTP, the BC shall give each session an isolated agent clone with its own conversation memory and shared tools and configuration.
/// - R5.2 — When serving the AgentCore runtime contract, the BC shall give each runtime session an isolated agent clone with its own conversation memory and shared tools and configuration.
///
/// ### R6: Manage conversation memory
/// - R6.1 — When memory is cleared, the BC shall discard the conversation history.
///
/// ### R7: Report version
/// - R7.1 — The BC shall report the framework version resolved from the packaged manifest or version file.
///
/// ## Entities
/// - AgentDefaults — configurable fallbacks for name, system prompt, iteration limit, temperature
/// - AgentTurnEvent — observable record of one loop iteration, carrying the run it belongs to
///
/// ## Out of scope
/// - LLM transport and API protocol (`llm`)
/// - tool implementations, schemas, and permission storage (`tools`, `configuration`)
/// - skill discovery and parsing (`skills`)
/// - episodic memory persistence (`episodicmemory`)
/// - HTTP server and session transport (`http`)
/// - sub-agent dispatch mechanics (`subagent`)
/// - the identity a run is recorded under and how it propagates (`correlation`)
/// - transcript storage and its format (`transcripts`)
/// - reading the recorded events back (`telemetry`)
package airhacks.zsmith.agent;
