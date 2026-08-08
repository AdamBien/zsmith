/// Lets an agent report where its own instructions failed it, so a human can fix
/// them. An agent is a good witness and a poor designer of its own prompt: it never
/// sees the counterfactual, so what it records here is an incident — the gap and the
/// input that exposed it — not a rewrite to apply.
///
/// Nothing written here feeds back into the agent. Reports are read and applied by a
/// human, deliberately: an agent editing its own prompt has no oversight, and a bad
/// edit changes the behaviour that would justify the next one.
package airhacks.zsmith.improvements;
