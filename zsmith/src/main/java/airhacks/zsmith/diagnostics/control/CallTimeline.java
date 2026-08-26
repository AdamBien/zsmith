package airhacks.zsmith.diagnostics.control;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jdk.jfr.consumer.RecordedEvent;

import airhacks.zsmith.agent.entity.AgentTurnEvent;
import airhacks.zsmith.claude.entity.ClaudeAPICallEvent;
import airhacks.zsmith.diagnostics.entity.Call;
import airhacks.zsmith.diagnostics.entity.Timeline;
import airhacks.zsmith.diagnostics.entity.ToolCall;
import airhacks.zsmith.lightmetal.entity.LightMetalAPICallEvent;
import airhacks.zsmith.openai.entity.OpenAIAPICallEvent;
import airhacks.zsmith.tools.entity.ToolInvocationEvent;

/// Collects the recording into one ordered [Timeline] per run.
///
/// The counterpart of the fold in `telemetry`, not a replacement for it: that answers what a run
/// cost by summing, this answers what happened in what order. Both read the same recording, and
/// the questions that need sequence — did a prefix expire, how long did a large result ride the
/// conversation — cannot be asked of a sum.
///
/// Events without a run id are dropped, matching the fold: they happened outside any chat loop.
///
/// Traces are sorted by start time when they are handed over rather than assumed to arrive in
/// order — parallel tools commit from their own virtual threads, and a chunk is not a promise
/// about interleaving.
public class CallTimeline implements Consumer<RecordedEvent> {

    static final List<String> EVENT_NAMES = List.of(
            AgentTurnEvent.NAME,
            ToolInvocationEvent.NAME,
            ClaudeAPICallEvent.NAME,
            OpenAIAPICallEvent.NAME,
            LightMetalAPICallEvent.NAME);

    private final ConcurrentHashMap<String, Trace> traces = new ConcurrentHashMap<>();

    /// The events a timeline is built from. Narrower than the fold's: a skill read or a memory
    /// write costs nothing that ordering explains.
    public static List<String> eventNames() {
        return EVENT_NAMES;
    }

    @Override
    public void accept(RecordedEvent event) {
        var runId = text(event, "runId");
        if (runId.isBlank()) {
            return;
        }
        var trace = this.traces.computeIfAbsent(runId, Trace::new);
        switch (event.getEventType().getName()) {
            case AgentTurnEvent.NAME -> trace.addTurn(event);
            case ToolInvocationEvent.NAME -> trace.addToolCall(event);
            case ClaudeAPICallEvent.NAME, OpenAIAPICallEvent.NAME, LightMetalAPICallEvent.NAME ->
                trace.addCall(event);
            default -> {
            }
        }
    }

    public Map<String, Timeline> timelines() {
        return this.traces.values().stream()
                .collect(Collectors.toUnmodifiableMap(Trace::runId, Trace::freeze));
    }

    /// Mutable on purpose: this accumulates across a stream of events, which is the one thing a
    /// record cannot do. It exists only until [#freeze] hands over the immutable view.
    static final class Trace {

        private final String runId;
        private final List<Call> calls = new ArrayList<>();
        private final List<ToolCall> toolCalls = new ArrayList<>();
        private String agent = "";
        private String parentRunId = "";
        private int depth;
        private int toolUses;
        private int turnsWithTools;

        Trace(String runId) {
            this.runId = runId;
        }

        String runId() {
            return this.runId;
        }

        synchronized void addTurn(RecordedEvent event) {
            if (this.agent.isBlank()) {
                this.agent = text(event, "agentName");
            }
            if (this.parentRunId.isBlank()) {
                this.parentRunId = text(event, "parentRunId");
            }
            this.depth = Math.max(this.depth, number(event, "depth"));
            var toolUses = number(event, "toolUseCount");
            this.toolUses += toolUses;
            if (toolUses > 0) {
                this.turnsWithTools++;
            }
        }

        synchronized void addCall(RecordedEvent event) {
            this.calls.add(new Call(this.runId, event.getStartTime(), event.getEndTime(),
                    number(event, "iteration"), number(event, "cacheReadTokens"),
                    number(event, "cacheCreationTokens")));
        }

        synchronized void addToolCall(RecordedEvent event) {
            this.toolCalls.add(new ToolCall(this.runId, number(event, "iteration"),
                    text(event, "toolName"), number(event, "resultSize")));
        }

        synchronized Timeline freeze() {
            this.calls.sort(Comparator.comparing(Call::started));
            this.toolCalls.sort(Comparator.comparingInt(ToolCall::iteration));
            return new Timeline(this.runId, this.agent, this.parentRunId, this.depth,
                    this.calls, this.toolCalls, this.toolUses, this.turnsWithTools);
        }
    }

    /// Read defensively for the same reason the fold does: a recording outlives the code that
    /// wrote it, and a field added after the fact must replay as zero rather than fail the read.
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
