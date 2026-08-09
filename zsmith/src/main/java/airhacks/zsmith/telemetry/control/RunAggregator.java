package airhacks.zsmith.telemetry.control;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import jdk.jfr.consumer.RecordedEvent;

import airhacks.zsmith.agent.entity.AgentTurnEvent;
import airhacks.zsmith.claude.entity.ClaudeAPICallEvent;
import airhacks.zsmith.lightmetal.entity.LightMetalAPICallEvent;
import airhacks.zsmith.openai.entity.OpenAIAPICallEvent;
import airhacks.zsmith.subagent.entity.SubAgentDispatchEvent;
import airhacks.zsmith.telemetry.entity.RunReport;
import airhacks.zsmith.telemetry.entity.TokenUsage;
import airhacks.zsmith.tools.entity.ToolInvocationEvent;

/// Folds recorded events into one [RunReport] per run id. Each event answers for itself what
/// it contributes, and the reports combine — so adding an event type means adding one case,
/// never touching the arithmetic.
///
/// Events without a run id are dropped: they happened outside any chat loop (a skill read at
/// store construction, a tool driven straight from a test) and belong to no run.
///
/// Fields are read defensively through [#number] and [#text] so that a recording made before
/// a field existed replays with zeroes instead of throwing — a `.jfr` outlives the code that
/// wrote it, which is most of why keeping it around is worth anything.
public class RunAggregator implements Consumer<RecordedEvent> {

    static final String RUN_ID = "runId";

    private final ConcurrentHashMap<String, RunReport> reports = new ConcurrentHashMap<>();

    @Override
    public void accept(RecordedEvent event) {
        var runId = text(event, RUN_ID);
        if (runId.isBlank()) {
            return;
        }
        this.reports.merge(runId, contributionOf(event, runId), RunReport::plus);
    }

    public Map<String, RunReport> reports() {
        return Map.copyOf(this.reports);
    }

    static RunReport contributionOf(RecordedEvent event, String runId) {
        return switch (event.getEventType().getName()) {
            case AgentTurnEvent.NAME -> fromTurn(event, runId);
            case ToolInvocationEvent.NAME -> fromToolInvocation(event, runId);
            case ClaudeAPICallEvent.NAME -> fromApiCall(event, runId, number(event, "attempt") > 1 ? 1 : 0);
            case OpenAIAPICallEvent.NAME, LightMetalAPICallEvent.NAME -> fromApiCall(event, runId, 0);
            case SubAgentDispatchEvent.NAME -> fromDispatch(runId);
            default -> RunReport.empty(runId);
        };
    }

    static RunReport fromTurn(RecordedEvent event, String runId) {
        return new RunReport(runId, text(event, "agentName"), number(event, "depth"), 1, 0, 0, Map.of(), 0, 0, 0,
                TokenUsage.NONE, event.getBoolean("terminal"));
    }

    /// A tool that was denied or missing never ran, but the turn still spent a round trip on
    /// it — counted as a call and as a failure, keyed by the outcome rather than by an
    /// exception it never got as far as throwing.
    static RunReport fromToolInvocation(RecordedEvent event, String runId) {
        var outcome = text(event, "outcome");
        var failed = !"success".equals(outcome);
        var kind = failureKind(text(event, "errorType"), outcome);
        return new RunReport(runId, "", 0, 0, 1, failed ? 1 : 0, failed ? Map.of(kind, 1) : Map.of(), 0, 0, 0,
                TokenUsage.NONE, false);
    }

    static String failureKind(String errorType, String outcome) {
        if (!errorType.isBlank()) {
            return errorType;
        }
        return outcome.isBlank() ? "unknown" : outcome;
    }

    static RunReport fromApiCall(RecordedEvent event, String runId, int retries) {
        return new RunReport(runId, "", 0, 0, 0, 0, Map.of(), 0, 1, retries, tokensOf(event), false);
    }

    static RunReport fromDispatch(String runId) {
        return new RunReport(runId, "", 0, 0, 0, 0, Map.of(), 1, 0, 0, TokenUsage.NONE, false);
    }

    static TokenUsage tokensOf(RecordedEvent event) {
        return new TokenUsage(number(event, "inputTokens"), number(event, "outputTokens"),
                number(event, "cacheReadTokens"), number(event, "cacheCreationTokens"));
    }

    static int number(RecordedEvent event, String field) {
        return event.hasField(field) ? event.getInt(field) : 0;
    }

    static String text(RecordedEvent event, String field) {
        if (!event.hasField(field)) {
            return "";
        }
        var value = event.getString(field);
        return value == null ? "" : value;
    }
}
