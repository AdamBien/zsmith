package airhacks.zsmith.improvements.entity;

import java.time.Instant;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/// A single reported gap in an agent's instructions.
///
/// `observation` and `trigger` are mandatory and `suggestion` is not, on purpose:
/// what the instruction failed to say, and the input that exposed it, are things
/// only the agent in that turn can supply. The rewrite is the reader's job.
public record Improvement(ArtifactKind artifact, String name, String observation, String trigger,
        String suggestion, String timestamp) {

    static final String ARTIFACT = "artifact";
    static final String NAME = "name";
    static final String OBSERVATION = "observation";
    static final String TRIGGER = "trigger";
    static final String SUGGESTION = "suggestion";
    static final String TIMESTAMP = "timestamp";

    /// Names the system prompt, which has no name of its own.
    public static final String SYSTEM_PROMPT = "system";

    public Improvement {
        if (observation == null || observation.isBlank()) {
            throw new IllegalArgumentException("Improvement observation must not be empty");
        }
        if (trigger == null || trigger.isBlank()) {
            throw new IllegalArgumentException("Improvement trigger must not be empty");
        }
        if (name == null || name.isBlank()) {
            name = SYSTEM_PROMPT;
        }
        if (suggestion == null) {
            suggestion = "";
        }
        if (timestamp == null) {
            timestamp = Instant.now().toString();
        }
    }

    public static Improvement of(ArtifactKind artifact, String name, String observation, String trigger,
            String suggestion) {
        return new Improvement(artifact, name, observation, trigger, suggestion, null);
    }

    /// The same gap reported twice is one gap, however it was worded the second time
    /// around — the trigger and the wording of a suggestion may differ.
    public boolean isSameObservation(Improvement other) {
        return this.artifact == other.artifact()
                && this.name.equals(other.name())
                && this.observation.equals(other.observation());
    }

    public String headline() {
        return "%s %s: %s".formatted(this.artifact, this.name, this.observation);
    }

    public SortedMap<String, String> toFields() {
        var fields = new TreeMap<String, String>();
        fields.put(ARTIFACT, this.artifact == null ? "" : this.artifact.name());
        fields.put(NAME, this.name);
        fields.put(OBSERVATION, this.observation);
        fields.put(TRIGGER, this.trigger);
        fields.put(TIMESTAMP, this.timestamp);
        if (!this.suggestion.isBlank()) {
            fields.put(SUGGESTION, this.suggestion);
        }
        return fields;
    }

    public static Improvement fromFields(Map<String, String> fields) {
        return new Improvement(ArtifactKind.fromString(emptyToNull(fields.get(ARTIFACT))), fields.get(NAME),
                fields.get(OBSERVATION), fields.get(TRIGGER), fields.get(SUGGESTION), fields.get(TIMESTAMP));
    }

    static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
