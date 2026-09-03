package airhacks.zsmith;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

/// Why this type or package exists, when the reason is a technical concern rather than a
/// business component's responsibility. Marks only what would not exist without the concern —
/// a domain class that emits an event participates in observability, it is not observability.
/// That restriction is what an import search cannot reproduce: `jdk.jfr` tells you what a class
/// touches, this says what it is for.
///
/// On a package when the whole business component serves the concern, on a type when it is
/// scattered across components that serve something else. Never both for the same class.
///
/// Retained in source only: every consumer — the reader, the agent, javadoc, ConcernTest —
/// reads the source, and the jar stays free of the metadata.
@Documented
@Retention(SOURCE)
@Target({TYPE, PACKAGE})
public @interface Concern {

    Kind value();

    /// Kinds are admitted only when the marked set is small, its members would not exist
    /// without the concern, and something enforces the marking. `OBSERVABILITY` qualifies:
    /// ConcernTest holds every JFR event type to it.
    enum Kind {
        OBSERVABILITY
    }
}
