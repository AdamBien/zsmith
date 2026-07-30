/// # AgentCore
/// > Expose a chat engine through the AWS Bedrock AgentCore runtime contract: session-scoped invocations in, result envelopes out, health on demand.
///
/// ## Boundary
/// - `serve-contract` — accept invocation and health requests for a chat engine on a configured port
/// - `invoke-agent` — run one engine invocation for a session's payload and answer with a result envelope
/// - `report-health` — answer a health probe with the runtime status
///
/// ## Requirements
/// ### R1: Serve the contract
/// - R1.1 — When the contract server is started, the BC shall accept invocation and health requests on the configured port on all interfaces.
/// - R1.2 — Where an ephemeral port is requested, the BC shall bind a free port and report the bound port. _(why: lets tests and local tooling run without port coordination)_
/// - R1.3 — When the server is stopped, the BC shall release the port and its workers.
///
/// ### R2: Invoke the agent
/// - R2.1 — When an invocation payload carries a prompt, the BC shall pass the prompt to the engine and answer with a success envelope containing the engine's reply.
/// - R2.2 — If an invocation payload carries no prompt, then the BC shall pass the raw payload to the engine. _(why: the contract allows arbitrary payloads; the agent decides what they mean)_
/// - R2.3 — When an invocation carries a runtime session id, the BC shall scope the engine conversation to that session and echo the id in the response.
/// - R2.4 — If an invocation carries no session id, then the BC shall generate one and echo it in the response.
/// - R2.5 — While an invocation of a session is in flight, the BC shall defer further invocations of the same session until it completes. _(why: one conversation, one turn at a time)_
/// - R2.6 — If an invocation payload is empty, then the BC shall reject the invocation.
/// - R2.7 — If an invocation arrives via an unsupported operation, then the BC shall refuse it.
/// - R2.8 — If the engine fails, then the BC shall answer with an error envelope instead of propagating the failure.
///
/// ### R3: Report health
/// - R3.1 — When health is probed, the BC shall answer healthy with the seconds-precision answer time.
/// - R3.2 — If a health probe arrives via an unsupported operation, then the BC shall refuse it.
///
/// ## Entities
/// - ResultEnvelope — response text and success or error status of one invocation
///
/// ## Out of scope
/// - the reasoning loop and per-session agent cloning (`agent`)
/// - generic chat and act transport (`http`)
/// - LLM protocol (`llm`)
package airhacks.zsmith.agentcore;
