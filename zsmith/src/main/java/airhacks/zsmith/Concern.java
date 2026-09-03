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
/// That restriction is what an import search cannot reproduce: an import says what a class
/// touches, this says what it is for.
///
/// On a package when the whole business component serves the concern, on a type when it is
/// scattered across components that serve something else. Never the same kind twice for one
/// class; a type may carry a kind its package does not declare.
///
/// Retained in source only: every consumer — the reader, the agent, javadoc, ConcernTest —
/// reads the source, and the jar stays free of the metadata.
@Documented
@Retention(SOURCE)
@Target({TYPE, PACKAGE})
public @interface Concern {

    Kind value();

    /// A kind is admitted only when its members are few, would not exist without the concern,
    /// and cannot be enumerated by an import search alone — otherwise the marker restates a
    /// grep. ConcernTest holds each one to the part of its set that is mechanically derivable.
    enum Kind {

        /// Exists to record what the process did: the JFR event types, and the components that
        /// capture and read recordings back. A class that emits an event in passing is not this.
        OBSERVABILITY,
        /// communication with external systems
        EXTERNAL
    }
}
