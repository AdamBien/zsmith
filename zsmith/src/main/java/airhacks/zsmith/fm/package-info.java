/// # Fm
/// > Make the on-device Apple Foundation Model a model provider, reached through the `fm` command and a local server this component runs itself.
///
/// ## Boundary
/// <!-- probing -->
/// - `check-availability` — answer whether the on-device model can serve, and the reason when it cannot
///
/// <!-- conversing -->
/// - `invoke-model` — send one turn of a conversation to the on-device model and answer its response
///
/// <!-- ending -->
/// - `stop-server` — stop the local server this component started
///
/// ## Requirements
///
/// ### R1: Check availability
/// - R1.1 — When the `fm` command is installed and reports the model as available, the BC shall answer that the model is available.
/// - R1.2 — If the `fm` command is not installed, then the BC shall answer that the model is unavailable and name the missing command.
/// - R1.3 — If the `fm` command reports the model as unavailable, then the BC shall answer that the model is unavailable together with the reason the command gave.
///
/// ### R2: Invoke the model
/// - R2.1 — When a turn is sent, the BC shall answer the model's text, its stop reason, the name of the model that served it and the tokens it used.
/// - R2.2 — The BC shall send the system prompt as the model's instructions.
/// - R2.3 — The BC shall send the whole conversation, including earlier tool calls and their results.
/// - R2.4 — While no model is configured, the BC shall address the model named `system`.
/// - R2.5 — While no output limit is configured, the BC shall limit a response to 4,096 output tokens.
/// - R2.6 — The BC shall send the temperature it is given.
/// - R2.7 — If the model is unavailable, then the BC shall fail the turn with the reason and start no server. _(why: a missing command or an unlicensed model must read as that, not as a server that never came up)_
/// - R2.8 — If the server answers a turn with an error, then the BC shall fail the turn with the server's message and not send it again.
///
/// ### R3: Offer tools without forcing them
/// - R3.1 — Where a turn carries tool definitions, the BC shall offer them to the model.
/// - R3.2 — If a turn requires a tool call, then the BC shall send it with the tool call left to the model. _(why: the on-device model refuses a forced tool call outright, and a turn answered in text beats a failed run)_
/// - R3.3 — When the model answers with a tool call, the BC shall answer the tool's name, its arguments and the call's id.
/// - R3.4 — When the model answers a turn carrying tool definitions in text, the BC shall answer that text with no tool call.
///
/// ### R4: Run the local server
/// - R4.1 — When the first turn is sent, the BC shall start the server and send the turn once the server reports healthy.
/// - R4.2 — While the server is healthy, the BC shall send every turn to it, whichever agent sends it and whether or not turns overlap. _(why: one on-device model serves the whole agent tree; a server per agent would pay the startup for each)_
/// - R4.3 — The BC shall make the server reachable from this machine only, on a free port it picks itself.
/// - R4.4 — The BC shall send turns only to the server it started. _(why: a server the user runs is theirs to configure and to stop)_
/// - R4.5 — While no turn has been sent, the BC shall start no server.
/// - R4.6 — If the server does not report healthy within the startup limit — 10 seconds unless configured — then the BC shall stop it and fail the turn with what the server printed.
/// - R4.7 — If the server is no longer running when a turn is sent, then the BC shall start it again once and send the turn.
/// - R4.8 — If the server started again does not report healthy either, then the BC shall fail the turn.
/// - R4.9 — When stopping is requested, the BC shall stop the server it started.
/// - R4.10 — When the process running the agents ends, the BC shall stop the server it started.
///
/// ### R5: Record the call
/// - R5.1 — When a call to the model completes, the BC shall record it with the run id, turn and depth it ran under, the model, the input and output tokens and the stop reason.
/// - R5.2 — When a call to the model completes, the BC shall add its tokens to the tally of the run it ran under.
///
/// ## Entities
/// - Availability, FmAPICallEvent
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - Choosing this provider — `llm`, which reads it as one more configured choice; it is never selected by detection and never fallen back from
/// - Making the model call a tool it was offered — the model's judgement; a turn may be answered in text
/// - Using a server the user started, or a configured address
/// - Invoking the command once per turn instead of through the server — it accepts no tool definitions and reports no token usage
/// - Streaming responses, images, the command's built-in tools, use cases and guardrail levels
/// - Installing the command or agreeing to its terms
package airhacks.zsmith.fm;
