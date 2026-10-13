package airhacks.zsmith.fm.boundary;

import java.time.Duration;

import airhacks.zsmith.configuration.control.HttpTimeouts;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.correlation.control.Correlations;
import airhacks.zsmith.fm.control.ChatCompletions;
import airhacks.zsmith.fm.control.FmCommand;
import airhacks.zsmith.fm.control.LocalServer;
import airhacks.zsmith.fm.entity.Availability;
import airhacks.zsmith.fm.entity.FmAPICallEvent;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.llm.entity.ToolChoice;
import airhacks.zsmith.logging.control.Log;
import airhacks.zsmith.telemetry.boundary.RunTally;
import airhacks.zsmith.telemetry.entity.TokenUsage;

/// The on-device Apple Foundation Model as a model provider: probed through the `fm`
/// command and spoken to through a local server this component starts on the first turn
/// and stops itself.
public record Fm(FmCommand command, LocalServer server) {

    public static final String COMMAND_KEY = "fm.command";
    public static final String STARTUP_TIMEOUT_KEY = "fm.startup.timeout";
    public static final Duration DEFAULT_STARTUP_LIMIT = Duration.ofSeconds(10);

    public static Fm of(String launchCommand, Duration startupLimit) {
        var command = FmCommand.of(launchCommand);
        return new Fm(command, new LocalServer(command, startupLimit));
    }

    /// The one provider every agent of this process shares, and with it the one server:
    /// the command from `fm.command`, the startup limit from `fm.startup.timeout` as an
    /// ISO-8601 duration.
    public static Fm configured() {
        return Configured.INSTANCE;
    }

    /// `check-availability` — whether the on-device model can serve, and the reason when it cannot.
    public Availability checkAvailability() {
        return this.command.checkAvailability(ChatCompletions.model());
    }

    /// `invoke-model` — send one turn of a conversation and answer the model's response in
    /// the normalized content-block shape, with the model that served it and its token usage.
    ///
    /// The tool choice is accepted for symmetry with the other providers and never sent.
    public JSONObject invokeModel(String system, JSONArray messages, JSONArray tools, float temperature,
            ToolChoice toolChoice) {
        var model = ChatCompletions.model();
        var payload = ChatCompletions.request(system, messages, tools, temperature).toString();
        Log.request(payload);
        Log.llm(">> " + payload);

        var endpoint = this.server.chatCompletions(model);
        var correlation = Correlations.current();
        var event = FmAPICallEvent.of(correlation, model);
        event.begin();
        Log.agent("using fm model: %s".formatted(model));
        var response = ChatCompletions.send(endpoint, payload);
        event.statusCode = response.statusCode();
        if (response.statusCode() != 200) {
            throw new IllegalStateException("fm server error %d: %s"
                    .formatted(response.statusCode(), ChatCompletions.errorMessage(response.body())));
        }

        var answer = ChatCompletions.answer(new JSONObject(response.body()));
        var usage = answer.getJSONObject("usage");
        event.model = answer.optString("model", model);
        event.stopReason = answer.optString("stop_reason", null);
        event.inputTokens = usage.getInt("input_tokens");
        event.outputTokens = usage.getInt("output_tokens");
        Log.tokens("in=%d out=%d/%d".formatted(event.inputTokens, event.outputTokens, ChatCompletions.maxTokens()));
        RunTally.tally(correlation.runId(), new TokenUsage(event.inputTokens, event.outputTokens, 0, 0));
        if (event.shouldCommit()) {
            event.commit();
        }

        var answered = answer.toString();
        Log.response(answered);
        Log.llm("<< " + answered);
        return answer;
    }

    /// `stop-server` — stop the local server this component started; nothing happens when none runs.
    public void stopServer() {
        this.server.stop();
    }

    /// Process-wide because one on-device model serves the whole agent tree, and built on
    /// first use so class initialization never depends on the configuration being loaded.
    static final class Configured {

        static final Fm INSTANCE = Fm.of(
                ZCfg.string(COMMAND_KEY, "fm"),
                HttpTimeouts.duration(STARTUP_TIMEOUT_KEY, DEFAULT_STARTUP_LIMIT));

        private Configured() {
        }
    }
}
