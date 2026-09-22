/// # Claude
/// > Decide which Claude model a request goes to and what that model accepts.
///
/// This doc declares the model-selection contract only; the call itself — endpoint,
/// credentials, retry, recording — is not yet declared.
///
/// ## Boundary
/// <!-- choosing -->
/// - `select-model` — resolve the active model from the configured name, the requested name, or the default
/// - `resolve-model-name` — return the name the selected model is sent under for the active provider
///
/// <!-- shaping -->
/// - `compose-request` — build a request that carries only the settings the selected model accepts
///
/// ## Requirements
///
/// ### R1: Offer Claude Opus 5
/// - R1.1 — The BC shall offer Claude Opus 5 as a selectable model that accepts effort and adaptive thinking and no temperature. _(why: the 5 generation rejects sampling parameters and thinks by default)_
/// - R1.2 — The BC shall allow Claude Opus 5 up to 64,000 output tokens per response.
/// - R1.3 — The BC shall name Claude Opus 4.8 as the model to retry with when Claude Opus 5 is overloaded. _(why: stay in the Opus tier rather than drop to a smaller model mid-run)_
/// - R1.4 — While neither a model is configured nor one is requested, the BC shall select Claude Opus 5.
///
/// ### R2: Select a model by name
/// - R2.1 — When a model is configured, the BC shall select the catalog model it names ahead of a requested name and the default. _(why: the selected model drives the token ceiling and accepted settings, and sending one model's name with another's settings is what the provider rejects)_
/// - R2.2 — When a partial name matches exactly one catalog model, the BC shall select it, disregarding case.
/// - R2.3 — If a partial name matches several catalog models, then the BC shall select the most recently released one. _(why: `opus` should mean the current Opus, not whichever entry happened to be declared first)_
/// - R2.4 — If the configured name matches no catalog model, then the BC shall select from the requested name, and failing that the default.
///
/// ### R3: Name the model for the provider
/// - R3.1 — The BC shall send the selected model under its catalog name.
/// - R3.2 — While Bedrock is the provider, when the name carries no namespace, the BC shall prefix it with the Anthropic namespace. _(why: one bare name in the configuration serves both providers)_
/// - R3.3 — If a configured name matches no catalog model, then the BC shall send the configured name as given. _(why: a model absent from the catalog stays reachable without a code change)_
///
/// ### R4: Carry only what the model accepts
/// - R4.1 — The BC shall include temperature only for a model that accepts it.
/// - R4.2 — The BC shall include effort only for a model that accepts it.
/// - R4.3 — The BC shall include a thinking mode only for a model that accepts it.
/// - R4.4 — If thinking is configured off while effort is configured above high for Claude Opus 5, then the BC shall send the request without a thinking mode and warn that adaptive thinking applies. _(why: the provider refuses that combination outright, and a warned request that succeeds beats a failed run)_
///
/// ## Decisions
/// - D1 — This doc declares model selection only, inside the existing `claude` BC. _(why: the feature touches nothing else; rejected: a full spec of the call contract in one step, and a separate `models` BC extracted from the catalog)_
/// - D2 — Claude Opus 5 is the default model. _(why: the current Opus generation; rejected: keeping Claude Opus 4.8 as default with Opus 5 opt-in)_
/// - D3 — Claude Opus 5 sends up to 64,000 output tokens. _(why: room for long answers without streaming; rejected: 32,000 as the older Opus entries, and 128,000 which needs streaming to avoid request timeouts)_
/// - D4 — Claude Opus 5 retries on overload with Claude Opus 4.8. _(why: same tier; rejected: Claude Sonnet 5, and the shared fallback name the other entries use)_
/// - D5 — An ambiguous partial name resolves to the most recently released match. _(why: matches how people abbreviate; rejected: failing on ambiguity with a candidate list)_
/// - D6 — Disabled thinking above high effort is dropped with a warning. _(why: the run completes; rejected: refusing the configuration before sending, and sending it unchanged so the provider's error surfaces)_
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - Refusal handling and server-side refusal fallbacks — a later capability
/// - Streaming responses — the reason the output ceiling stays below the model's maximum
/// - Sending the request: endpoint, credentials, headers, the overload retry itself, and the recorded call event — not yet declared
/// - Choosing the provider (native Anthropic, Bedrock, OpenAI-compatible) — configuration read by `llm`
/// - Models spoken over the OpenAI-compatible route — translated by `openai`
package airhacks.zsmith.claude;
