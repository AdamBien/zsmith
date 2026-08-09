package airhacks.zsmith.transcripts.entity;

import java.time.Instant;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/// What one chat loop actually said and did, stored under the same `runId` its JFR events
/// carry. The events answer which run is worth looking at; this answers what happened in it.
public record Transcript(String runId, String agent, String outcome, int turns, String conversation,
        String timestamp) {

    static final String RUN_ID = "runId";
    static final String AGENT = "agent";
    static final String OUTCOME = "outcome";
    static final String TURNS = "turns";
    static final String CONVERSATION = "conversation";
    static final String TIMESTAMP = "timestamp";

    public Transcript {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("Transcript runId must not be empty");
        }
        if (conversation == null) {
            conversation = "";
        }
        if (timestamp == null) {
            timestamp = Instant.now().toString();
        }
    }

    public static Transcript of(String runId, String agent, String outcome, int turns, String conversation) {
        return new Transcript(runId, agent, outcome, turns, conversation, null);
    }

    public SortedMap<String, String> toFields() {
        var fields = new TreeMap<String, String>();
        fields.put(RUN_ID, this.runId);
        fields.put(AGENT, this.agent);
        fields.put(OUTCOME, this.outcome);
        fields.put(TURNS, String.valueOf(this.turns));
        fields.put(CONVERSATION, this.conversation);
        fields.put(TIMESTAMP, this.timestamp);
        return fields;
    }

    public static Transcript fromFields(Map<String, String> fields) {
        return new Transcript(fields.get(RUN_ID), fields.get(AGENT), fields.get(OUTCOME),
                turnsOf(fields.get(TURNS)), fields.get(CONVERSATION), fields.get(TIMESTAMP));
    }

    static int turnsOf(String turns) {
        if (turns == null || turns.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(turns.trim());
        } catch (NumberFormatException _) {
            return 0;
        }
    }
}
