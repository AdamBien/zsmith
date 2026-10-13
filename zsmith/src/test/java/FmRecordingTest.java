import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.correlation.control.Correlations;
import airhacks.zsmith.correlation.entity.Correlation;
import airhacks.zsmith.fm.boundary.Fm;
import airhacks.zsmith.fm.entity.FmAPICallEvent;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.llm.entity.ToolChoice;
import airhacks.zsmith.telemetry.boundary.RunTally;

/// Traces fm spec R5.1, R5.2 — see src/main/java/airhacks/zsmith/fm/package-info.java
/// The `fm` command is src/test/java/fm/FakeFm.java; it logs how it was launched and every turn it receives.

void main() throws Exception {
    ZCfg.loadBaseConfig("zsmith-test-fm-" + ProcessHandle.current().pid());
    var log = Files.createTempFile("zunit-fm", ".log");
    var fm = Fm.of(fakeFm(log), Duration.ofSeconds(10));
    var correlation = Correlation.start(Correlation.NONE.deeper()).atIteration(2);
    var latch = new CountDownLatch(1);
    var recorded = new AtomicReference<RecordedEvent>();
    try (var stream = new RecordingStream()) {
        stream.enable(FmAPICallEvent.NAME);
        stream.onEvent(FmAPICallEvent.NAME, event -> {
            recorded.set(event);
            latch.countDown();
        });
        stream.startAsync();
        Thread.sleep(1_000);

        ScopedValue.where(Correlations.CURRENT, correlation)
                .call(() -> fm.invokeModel(null, user("hello"), null, 0.5f, ToolChoice.auto));

        // R5.1 — When a call to the model completes, the BC shall record it with the run id, turn and
        // depth it ran under, the model, the input and output tokens and the stop reason.
        assert latch.await(10, TimeUnit.SECONDS) : "R5.1 — expected the call recorded but no event arrived";
        var event = recorded.get();
        assert correlation.runId().equals(event.getString("runId"))
                && event.getInt("iteration") == 2 && event.getInt("depth") == 1
                && "system".equals(event.getString("model"))
                && event.getInt("inputTokens") == 65 && event.getInt("outputTokens") == 3
                && "end_turn".equals(event.getString("stopReason"))
                : "R5.1 — expected run id, turn, depth, model, tokens and stop reason but recorded: " + event;

        // R5.2 — When a call to the model completes, the BC shall add its tokens to the tally of the
        // run it ran under.
        var tally = RunTally.runningTokens(correlation.runId());
        assert tally.input() == 65 && tally.output() == 3
                : "R5.2 — expected the call's tokens on the run's tally but got: " + tally;
    } finally {
        fm.stopServer();
        RunTally.discard(correlation.runId());
        Files.deleteIfExists(log);
    }
}

static JSONArray user(String text) {
    return new JSONArray().put(new JSONObject().put("role", "user").put("content", text));
}

static String fakeFm(Path log, String... flags) {
    var fake = Path.of("src/test/java/fm/FakeFm.java").toAbsolutePath();
    assert Files.exists(fake) : "fake fm command not found at " + fake;
    return String.join(" ", "java", "--source", "25", "--class-path", classpath(),
            fake.toString(), log.toString(), String.join(" ", flags)).strip();
}

static String classpath() {
    return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(entry -> Path.of(entry).toAbsolutePath().toString())
            .collect(Collectors.joining(File.pathSeparator));
}

static List<String> entries(Path log, String kind) throws IOException {
    if (!Files.exists(log)) {
        return List.of();
    }
    return Files.readAllLines(log).stream().filter(line -> line.startsWith(kind)).toList();
}
