package airhacks.zsmith.correlation.control;

import airhacks.zsmith.correlation.entity.Correlation;

/// Carries the current [Correlation] to code the chat loop calls but does not pass it
/// to — the static LLM transports, which run on the loop's own thread.
///
/// Deliberately not used for tool execution: parallel-capable tools are submitted to a
/// virtual-thread executor, and a scoped value binding is inherited only by threads
/// forked from a structured task scope, never by a plain executor submit. Reading it
/// there would silently answer [Correlation#NONE], so tool execution takes the
/// correlation as an argument instead.
public interface Correlations {

    ScopedValue<Correlation> CURRENT = ScopedValue.newInstance();

    static Correlation current() {
        return CURRENT.orElse(Correlation.NONE);
    }
}
