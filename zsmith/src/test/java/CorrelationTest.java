import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.agent.entity.AgentTurnEvent;
import airhacks.zsmith.benchmark.control.LookupTool;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.subagent.entity.SubAgentDispatchEvent;
import airhacks.zsmith.tools.entity.ToolInvocationEvent;

/// Traces agent spec R2.9, R3.6 and R3.7 — see src/main/java/airhacks/zsmith/agent/package-info.java
///
/// Every scenario asserts across the executor boundary on purpose. A scoped value is not
/// inherited by a thread submitted to a plain executor, so a correlation read from ambient
/// scope inside a parallel tool would answer blank and every one of these would fail.

record Turn(String agent, String runId, String parentRunId, int depth, int iteration, boolean terminal) {

    static Turn of(RecordedEvent event) {
        return new Turn(event.getString("agentName"), event.getString("runId"), event.getString("parentRunId"),
                event.getInt("depth"), event.getInt("iteration"), event.getBoolean("terminal"));
    }
}

record Invocation(String runId, String toolUseId, String toolName, String outcome, String errorType) {

    static Invocation of(RecordedEvent event) {
        return new Invocation(event.getString("runId"), event.getString("toolUseId"), event.getString("toolName"),
                event.getString("outcome"), event.getString("errorType"));
    }
}

/// Serves a fixed sequence of Anthropic-shaped responses, one per request, so a multi-turn
/// conversation is scripted rather than guessed at.
///
/// One server for the whole test, replayed with a new script per scenario: the endpoint is
/// snapshotted into the configuration cache when the first agent loads, so a second server on
/// a second port would never be talked to.
final class ScriptedLLM implements AutoCloseable {

    final HttpServer server;
    final AtomicInteger served = new AtomicInteger();
    volatile List<String> script = List.of();

    ScriptedLLM() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(0), 0);
        this.server.createContext("/v1/messages", this::handle);
        this.server.start();
        configure(this.server.getAddress().getPort());
    }

    ScriptedLLM play(List<String> responses) {
        this.script = responses;
        this.served.set(0);
        return this;
    }

    void handle(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        var index = this.served.getAndIncrement();
        var current = this.script;
        var body = index < current.size() ? current.get(index) : endTurn("script exhausted");
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static void configure(int port) {
        System.setProperty("llm.provider", "claude");
        System.setProperty("claude.model", "claude-opus-4-8");
        System.setProperty("claude.scheme", "http");
        System.setProperty("claude.host", "localhost");
        System.setProperty("claude.port", String.valueOf(port));
        System.setProperty("claude.path", "/v1/messages");
        System.setProperty("anthropic.api.key", "test-key");
        System.setProperty("anthropic.version", "2023-06-01");
        System.setProperty("tools.permissions.default", "allow");
    }

    static String endTurn(String text) {
        return new JSONObject()
                .put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", text)))
                .put("stop_reason", "end_turn")
                .toString();
    }

    static String toolUse(Map<String, JSONObject> callsById, String toolName) {
        var content = new JSONArray();
        callsById.forEach((id, input) -> content.put(new JSONObject()
                .put("type", "tool_use")
                .put("id", id)
                .put("name", toolName)
                .put("input", input)));
        return new JSONObject().put("content", content).put("stop_reason", "tool_use").toString();
    }

    @Override
    public void close() {
        this.server.stop(0);
    }
}

/// Collects the correlated events of one scenario, and waits until the expected number have
/// been flushed rather than sleeping and hoping.
final class Recorded implements AutoCloseable {

    final ConcurrentLinkedQueue<Turn> turns = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<Invocation> invocations = new ConcurrentLinkedQueue<>();
    final ConcurrentLinkedQueue<String> dispatchRunIds = new ConcurrentLinkedQueue<>();
    final CountDownLatch expected;
    final RecordingStream stream;

    Recorded(int expectedEvents) throws Exception {
        this.expected = new CountDownLatch(expectedEvents);
        this.stream = new RecordingStream();
        this.stream.enable(AgentTurnEvent.NAME);
        this.stream.enable(ToolInvocationEvent.NAME);
        this.stream.enable(SubAgentDispatchEvent.NAME);
        this.stream.onEvent(AgentTurnEvent.NAME, event -> record(this.turns, Turn.of(event)));
        this.stream.onEvent(ToolInvocationEvent.NAME, event -> record(this.invocations, Invocation.of(event)));
        this.stream.onEvent(SubAgentDispatchEvent.NAME,
                event -> record(this.dispatchRunIds, event.getString("runId")));
        this.stream.startAsync();
        // events committed before the stream is running are never delivered
        Thread.sleep(1_000);
    }

    <T> void record(ConcurrentLinkedQueue<T> sink, T value) {
        sink.add(value);
        this.expected.countDown();
    }

    void awaitAll(String scenario) throws InterruptedException {
        if (!this.expected.await(15, TimeUnit.SECONDS)) {
            throw new AssertionError("%s — only %d/%d events arrived: turns=%s invocations=%s dispatches=%s"
                    .formatted(scenario, this.turns.size() + this.invocations.size() + this.dispatchRunIds.size(),
                            this.turns.size() + this.invocations.size() + this.dispatchRunIds.size()
                                    + (int) this.expected.getCount(),
                            this.turns, this.invocations, this.dispatchRunIds));
        }
    }

    @Override
    public void close() {
        this.stream.close();
    }
}

void main() throws Exception {
    try (var llm = new ScriptedLLM()) {
        correlatesTurnsWithToolCallsIssuedInParallel(llm);
        linksASequentialSubAgentRunToTheRunThatDelegated(llm);
        keepsTheDepthOfASubAgentDispatchedInParallel(llm);
    }
}

// R3.6 — tool events carry the run, the turn, and the model's own tool-use id.
void correlatesTurnsWithToolCallsIssuedInParallel(ScriptedLLM llm) throws Exception {
    var lookups = new LookupTool(Map.of("alpha", "01", "beta", "02"));
    llm.play(List.of(
            ScriptedLLM.toolUse(Map.of(
                    "toolu_alpha", new JSONObject().put("id", "alpha"),
                    "toolu_beta", new JSONObject().put("id", "beta")), lookups.toolName()),
            ScriptedLLM.endTurn("done")));

    try (var recorded = new Recorded(4)) {
        new Agent("correlated-agent", "prompt").withTool(lookups).chat("go");
        recorded.awaitAll("R3.6");

        var turns = List.copyOf(recorded.turns);
        var invocations = List.copyOf(recorded.invocations);
        if (turns.size() != 2)
            throw new AssertionError("R2.9 — expected 2 turns but got: " + turns);
        if (invocations.size() != 2)
            throw new AssertionError("R3.6 — expected 2 tool invocations but got: " + invocations);

        var runId = turns.getFirst().runId();
        if (runId == null || runId.isBlank())
            throw new AssertionError("R2.9 — a turn must carry a run id but got: " + turns.getFirst());
        for (var turn : turns) {
            if (!runId.equals(turn.runId()))
                throw new AssertionError("R2.9 — every turn of one chat shares its run id but got: " + turns);
            if (turn.depth() != 0 || !turn.parentRunId().isBlank())
                throw new AssertionError("R2.9 — a top-level chat is depth 0 with no parent but got: " + turn);
        }
        for (var invocation : invocations) {
            if (!runId.equals(invocation.runId()))
                throw new AssertionError(
                        "R3.6 — a tool call issued in parallel must carry the run that issued it, got: " + invocation);
            if (!"success".equals(invocation.outcome()) || invocation.errorType() != null)
                throw new AssertionError("R3.6 — a successful call carries no failure type, got: " + invocation);
        }
        var toolUseIds = invocations.stream().map(Invocation::toolUseId).sorted().toList();
        if (!List.of("toolu_alpha", "toolu_beta").equals(toolUseIds))
            throw new AssertionError("R3.6 — expected the model's own tool-use ids but got: " + toolUseIds);
    }
}

// R2.9 — a delegated run points back at the run that delegated to it.
void linksASequentialSubAgentRunToTheRunThatDelegated(ScriptedLLM llm) throws Exception {
    var child = new Agent("sequential-child", "prompt");
    var parent = new Agent("sequential-parent", "prompt").withSequentialSubAgent(child);
    llm.play(List.of(
            ScriptedLLM.toolUse(Map.of("toolu_delegate", new JSONObject().put("task", "look into it")),
                    "delegate_to_" + child.name()),
            ScriptedLLM.endTurn("child done"),
            ScriptedLLM.endTurn("parent done")));

    try (var recorded = new Recorded(5)) {
        parent.chat("go");
        recorded.awaitAll("R2.9 sub-agent");
        assertChildLinksToParent(recorded, child.name(), parent.name());
    }
}

/// The regression this guards: a sub-agent whose dispatch runs on the virtual-thread
/// executor. Its depth used to be read from ambient scope, which that thread does not
/// inherit, so a nested agent silently restarted the count at zero and the depth ceiling
/// stopped meaning anything.
// R3.7 — When a tool is executed concurrently, the BC shall make the run identifier available
// to what the tool itself invokes: this child is dispatched from inside a parallel tool, so it
// can only name its parent run if the correlation crossed the executor.
void keepsTheDepthOfASubAgentDispatchedInParallel(ScriptedLLM llm) throws Exception {
    var home = Files.createTempDirectory("zsmith-parallel-subagent");
    var originalHome = System.getProperty("user.home");
    System.setProperty("user.home", home.toString());
    try {
        var child = new Agent("parallel-child", "prompt");
        var parent = new Agent("parallel-parent", "prompt").withSubAgent(child);
        markFirstRunCompleted(child.name());

        llm.play(List.of(
                ScriptedLLM.toolUse(Map.of("toolu_delegate", new JSONObject().put("task", "look into it")),
                        "delegate_to_" + child.name()),
                ScriptedLLM.endTurn("child done"),
                ScriptedLLM.endTurn("parent done")));

        try (var recorded = new Recorded(5)) {
            parent.chat("go");
            recorded.awaitAll("R2.9/R3.7 parallel sub-agent");
            assertChildLinksToParent(recorded, child.name(), parent.name());
        }
    } finally {
        System.setProperty("user.home", originalHome);
    }
}

void markFirstRunCompleted(String agentName) throws Exception {
    var marker = ZCfg.agentDirectory(agentName).resolve(".first_run_completed");
    Files.createDirectories(marker.getParent());
    Files.writeString(marker, "");
    if (!Files.exists(marker))
        throw new AssertionError("could not arm the parallel dispatch marker at " + marker);
}

void assertChildLinksToParent(Recorded recorded, String childName, String parentName) {
    var turns = List.copyOf(recorded.turns);
    var parentTurns = turns.stream().filter(turn -> parentName.equals(turn.agent())).toList();
    var childTurns = turns.stream().filter(turn -> childName.equals(turn.agent())).toList();
    if (parentTurns.size() != 2 || childTurns.size() != 1)
        throw new AssertionError("R2.9 — expected 2 parent turns and 1 child turn but got: " + turns);

    var parentRun = parentTurns.getFirst().runId();
    var childTurn = childTurns.getFirst();
    if (childTurn.runId().equals(parentRun))
        throw new AssertionError("R2.9 — a delegated run is its own run, not the parent's: " + childTurn);
    if (!parentRun.equals(childTurn.parentRunId()))
        throw new AssertionError("R2.9 — the child must point back at run %s but got: %s"
                .formatted(parentRun, childTurn));
    if (childTurn.depth() != 1)
        throw new AssertionError("R2.9 — a delegated run is one level deeper, got: " + childTurn);

    var dispatches = List.copyOf(recorded.dispatchRunIds);
    if (!List.of(parentRun).equals(dispatches))
        throw new AssertionError("R2.9 — the dispatch belongs to the delegating run %s but got: %s"
                .formatted(parentRun, dispatches));
}
