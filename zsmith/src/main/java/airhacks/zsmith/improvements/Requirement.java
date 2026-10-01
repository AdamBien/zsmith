package airhacks.zsmith.improvements;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Generated from the capability spec in [airhacks.zsmith.improvements] — do not edit.
/// Marks the boundary method or test that realizes the given requirement statements.
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Requirement {

    /// One constant per statement id in the spec's `## Requirements`.
    enum Rn {
        /// When an artifact kind, an observation and a trigger are supplied, the BC shall keep the report durably and answer that it was recorded.
        R1_1("R1.1", "When an artifact kind, an observation and a trigger are supplied, the BC shall keep the report durably and answer that it was recorded."),
        /// If the observation or the trigger is absent or blank, then the BC shall reject the report.
        R1_2("R1.2", "If the observation or the trigger is absent or blank, then the BC shall reject the report."),
        /// If the artifact kind is absent or is none of prompt, skill and tool, then the BC shall reject the report.
        R1_3("R1.3", "If the artifact kind is absent or is none of prompt, skill and tool, then the BC shall reject the report."),
        /// The BC shall accept the artifact kind regardless of case.
        R1_4("R1.4", "The BC shall accept the artifact kind regardless of case."),
        /// If a prompt report names no artifact, then the BC shall record it under the name `system`.
        R1_5("R1.5", "If a prompt report names no artifact, then the BC shall record it under the name `system`."),
        /// If a skill or tool report names no artifact, then the BC shall reject the report.
        R1_6("R1.6", "If a skill or tool report names no artifact, then the BC shall reject the report."),
        /// Where a suggestion is supplied, the BC shall keep it with the report; otherwise the BC shall give the suggestion back as empty.
        R1_7("R1.7", "Where a suggestion is supplied, the BC shall keep it with the report; otherwise the BC shall give the suggestion back as empty."),
        /// When a report is made without a time, the BC shall record the time it was made.
        R1_8("R1.8", "When a report is made without a time, the BC shall record the time it was made."),
        /// When distinct reports are made within the same second, the BC shall keep each of them.
        R1_9("R1.9", "When distinct reports are made within the same second, the BC shall keep each of them."),

        /// If a report with the same artifact kind, name and observation is already kept, then the BC shall keep the stored one unchanged and answer that nothing was recorded.
        R2_1("R2.1", "If a report with the same artifact kind, name and observation is already kept, then the BC shall keep the stored one unchanged and answer that nothing was recorded."),
        /// The BC shall decide sameness by the artifact kind, the name and the exact observation text alone, disregarding trigger, suggestion and time.
        R2_2("R2.2", "The BC shall decide sameness by the artifact kind, the name and the exact observation text alone, disregarding trigger, suggestion and time."),
        /// The BC shall apply the sameness check to reports kept in earlier sessions as well.
        R2_3("R2.3", "The BC shall apply the sameness check to reports kept in earlier sessions as well."),

        /// The BC shall return every kept report, oldest first, with its artifact kind, name, observation, trigger, suggestion and time.
        R3_1("R3.1", "The BC shall return every kept report, oldest first, with its artifact kind, name, observation, trigger, suggestion and time."),
        /// The BC shall return a kept report in a later session.
        R3_2("R3.2", "The BC shall return a kept report in a later session."),
        /// If a kept report cannot be read, then the BC shall skip it and return the rest.
        R3_3("R3.3", "If a kept report cannot be read, then the BC shall skip it and return the rest."),

        /// When a backlog is opened for a named agent, the BC shall keep its reports in that agent's own database, alongside its memories.
        R4_1("R4.1", "When a backlog is opened for a named agent, the BC shall keep its reports in that agent's own database, alongside its memories."),
        /// The BC shall keep the reports as a table browsable without this component.
        R4_2("R4.2", "The BC shall keep the reports as a table browsable without this component."),

        /// When clearing is requested, the BC shall drop every report from the run and from its database.
        R5_1("R5.1", "When clearing is requested, the BC shall drop every report from the run and from its database."),

        /// When a model reports a gap, the BC shall answer whether the report was recorded, was already reported, or was refused together with the reason.
        R6_1("R6.1", "When a model reports a gap, the BC shall answer whether the report was recorded, was already reported, or was refused together with the reason.");

        private final String id;
        private final String statement;

        Rn(String id, String statement) {
            this.id = id;
            this.statement = statement;
        }

        public String statement() {
            return this.statement;
        }

        @Override
        public String toString() {
            return this.id;
        }
    }

    Rn[] value();
}
