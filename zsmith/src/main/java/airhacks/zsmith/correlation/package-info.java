/// Gives every event emitted during one chat loop a shared identity, so the JDK Flight
/// Recorder stream can be joined rather than guessed at: turn to tool call to LLM call to
/// sub-agent dispatch, keyed by run id.
///
/// Its own component rather than part of `agent`, because the LLM transports read it and
/// `agent` already declares LLM transport out of scope — the dependency has to point the
/// other way.
///
/// The correlation is a key, not a payload. What actually happened in a run stays where it
/// already lives (`memory`, `transcripts`); the events carry the key and enough categorical
/// detail to find the interesting runs.
package airhacks.zsmith.correlation;
