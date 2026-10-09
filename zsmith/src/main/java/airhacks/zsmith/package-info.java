/// # ZSmith
/// > Run zero-dependency AI agents on the JDK: an LLM reasoning loop with governed tools, memory and observability.
///
/// ## Components
/// <!-- this system's concrete wiring, declared as it is confirmed; direction matters; each BC's own contract lives in its package-info -->
/// - `lsp` may call `tools`: it fulfils tools' published tool contract; never the reverse. Path confinement stays inside `lsp`, since `tools` declares no path-resolution operation.
/// - `transcripts`, `episodicmemory` and `improvements` may call `htmldb`: each keeps its records as pages of a store of its own. `htmldb` calls no other BC and knows none of its callers.
///
/// ## Decisions
/// <!-- append-only confirmed choices with rejected alternatives; rationale, not contract — no test; supersede, never edit -->
/// - D1 — Language-server code navigation is its own BC, `lsp`. _(why: a protocol client with a child-process lifetime is a responsibility of its own, and `tools` already carries seventeen requirement groups; rejected: extending `tools` with the navigation handlers, and splitting a protocol-only `lsp` from a separate tool-facing BC)_
/// - D2 — `lsp` starts one language server per workspace from a configured launch command and speaks the protocol over the child's standard streams. _(why: any protocol-conforming server works with zero code, and the standard-stream transport needs no port; rejected: a per-language command map routing files to several servers, and connecting to an already running server over a socket)_
/// - D3 — Delegation to a sub-agent is a control of `agent`, not a BC of its own. _(why: a sub-agent is an agent wrapped as a tool, so the dispatch cannot exist without `agent` and nothing but `agent` constructs it — a separate `subagent` package was the only control-level cycle between two BCs, had no boundary and no spec, while `skills` and `episodicmemory`, which the loop equips the same way, depend on nothing in `agent`; rejected: keeping `subagent` as a peer BC, and folding the dispatch into `tools`, which knows no agent)_
///
/// ## Stack
/// - java-cli-app · base package `airhacks.zsmith` · build with `zb.sh` in `zsmith/` · tests with zunit
package airhacks.zsmith;
