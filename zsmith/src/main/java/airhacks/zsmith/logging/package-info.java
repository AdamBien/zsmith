/// # Logging
/// > Route what an agent says to the person using it, and what it reports about itself to wherever that belongs.
///
/// ## Boundary
/// - `direct-diagnostics` — send the routable channels of a named agent somewhere, answering where they landed
/// - `release-sink` — flush and release the diagnostic destination
///
/// ## Requirements
/// ### R1: Keep the conversation on the console
/// - R1.1 — The BC shall present what the agent asks and answers on the console, whatever destination diagnostics are directed to. _(why: the questions an agent blocks on are its user interface, so redirecting them leaves a run waiting for an answer against a blank terminal)_
/// - R1.2 — The BC shall present failures and warnings on the console, whatever destination diagnostics are directed to. _(why: a failure nobody sees is worse than one that clutters a prompt)_
/// - R1.3 — The BC shall present a run's progress on the console, whatever destination diagnostics are directed to. _(why: a long run with a silent terminal is indistinguishable from a hung one)_
///
/// ### R2: Direct the diagnostics elsewhere
/// - R2.1 — Where diagnostics are directed to a file, the BC shall write every routable channel to that file and none of them to the console.
/// - R2.2 — Where diagnostics are directed to both, the BC shall write every routable channel to the file and to the console.
/// - R2.3 — Where no file destination is in effect, the BC shall present every routable channel on the console. _(why: this is what the framework did before it could do anything else, and an upgrade must not silently move a user's output)_
/// - R2.4 — When diagnostics are directed for a named agent, the BC shall write them under that agent's own directory.
/// - R2.5 — When a diagnostic file is opened, the BC shall name it so that concurrent runs do not overwrite one another.
/// - R2.6 — If a diagnostic file cannot be opened, then the BC shall report the failure, present the routable channels on the console, and leave the run unaffected. _(why: observing a run must never be what ends it)_
/// - R2.7 — When the diagnostic destination is released, the BC shall flush what it holds and close it. _(why: a run's last words are the ones worth having, and an unflushed buffer loses exactly those)_
/// - R2.8 — The BC shall leave a switched-off channel switched off whatever the destination. _(why: where output goes is a separate question from whether it is produced; directing to a file must not start recording payloads nobody asked for)_
///
/// ## Entities
/// - Sink — where the routable channels go: the console, a file, or both
///
/// ## Decisions
/// - D1 — The declared boundary is the destination, not the emitter; the emitter stays in the control layer. _(why: two dozen call sites, a README example and live agent scripts import the emitter from `control`, and a published jar makes that package part of the contract; rejected: moving the emitter into `boundary`, which is the layering this project otherwise keeps but breaks every caller, and leaving this unspec'd infrastructure, which leaves the split with no contract to converge against)_
///
/// ## Out of scope
/// <!-- weighed and deliberately excluded -->
/// - What is worth reporting, and on which channel — the caller picks its channel, this only routes it
/// - Turning individual channels on and off — a separate question from where they go, and already answered per channel
/// - Retention, rotation and cleanup of what has been written
/// - Structured or machine-readable output — the file holds the same lines the console would have shown
/// - Capturing events for later scoring (`telemetry`), and storing conversations (`transcripts`) — both keep their own destinations
package airhacks.zsmith.logging;
