import static airhacks.zsmith.chunking.Requirement.Rn.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

import airhacks.zsmith.chunking.control.Chunking;
import airhacks.zsmith.chunking.control.Model;
import airhacks.zsmith.chunking.entity.ChunkProcessedEvent;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.llm.entity.LLMResponse;
import airhacks.zsmith.llm.entity.ToolChoice;

/// Traces chunking spec R2.1, R2.2, R2.3, R2.4, R2.5 — see src/main/java/airhacks/zsmith/chunking/package-info.java
/// The fake model records every turn it is sent and answers each chunk upper-cased.

record Call(String system, JSONArray messages, JSONArray tools, float temperature, ToolChoice toolChoice) {}

static final String INSTRUCTION = "Upper-case the text.";

/// Three chunks at a budget of ten: "alpha beta", "gamma.", "delta".
static final String TEXT = "alpha beta gamma.\n\ndelta";

void main() throws Exception {
    System.setProperty(Chunking.TEMPERATURE_KEY, "0.4");
    ZCfg.loadBaseConfig("zsmith-test-chunking-" + ProcessHandle.current().pid());

    var calls = new CopyOnWriteArrayList<Call>();
    var inFlight = new AtomicInteger();
    var overlapping = new AtomicInteger();
    Model model = (system, messages, tools, temperature, toolChoice) -> {
        if (inFlight.incrementAndGet() > 1) {
            overlapping.incrementAndGet();
        }
        calls.add(new Call(system, messages, tools, temperature, toolChoice));
        var chunk = messages.getJSONObject(0).getString("content");
        inFlight.decrementAndGet();
        return text(chunk.toUpperCase());
    };

    // R2.1 — When a text is split, the BC shall send the chunks in order, one at a time, each as the
    // only message of a fresh conversation, with the instruction as the system prompt and no tools offered.
    new Chunking(model, 10, 0.7f).processText(INSTRUCTION, TEXT);
    var sent = calls.stream().map(call -> call.messages().getJSONObject(0).getString("content")).toList();
    assert List.of("alpha beta", "gamma.", "delta").equals(sent)
            : "%s — %s: expected the chunks in order but sent %s".formatted(R2_1, R2_1.statement(), sent);
    assert overlapping.get() == 0
            : "%s — %s: expected one chunk at a time but %d overlapped".formatted(R2_1, R2_1.statement(), overlapping.get());
    for (var call : calls) {
        var message = call.messages().getJSONObject(0);
        assert call.messages().length() == 1 && "user".equals(message.getString("role"))
                : "%s — %s: expected the chunk as the only message but sent %s".formatted(R2_1, R2_1.statement(), call.messages());
        assert INSTRUCTION.equals(call.system())
                : "%s — %s: expected the instruction as system prompt but sent %s".formatted(R2_1, R2_1.statement(), call.system());
        assert call.tools() == null || call.tools().isEmpty()
                : "%s — %s: expected no tools offered but sent %s".formatted(R2_1, R2_1.statement(), call.tools());
        assert call.toolChoice() != ToolChoice.required
                : "%s — %s: expected no tool call demanded but sent %s".formatted(R2_1, R2_1.statement(), call.toolChoice());
    }
    var distinct = calls.stream().map(call -> System.identityHashCode(call.messages())).distinct().count();
    assert distinct == calls.size()
            : "%s — %s: expected a fresh conversation per chunk but %d conversations served %d chunks"
                    .formatted(R2_1, R2_1.statement(), distinct, calls.size());

    // R2.2 — The BC shall send every chunk at the configured temperature.
    assert calls.stream().allMatch(call -> call.temperature() == 0.7f)
            : "%s — %s: expected every chunk at 0.7 but sent %s".formatted(R2_2, R2_2.statement(),
                    calls.stream().map(Call::temperature).toList());
    var configured = Chunking.configured().temperature();
    assert Math.abs(configured - 0.4f) < 0.0001
            : "%s — %s: expected the configured temperature 0.4 but got %s".formatted(R2_2, R2_2.statement(), configured);

    // R2.3 — If a chunk is blank, then the BC shall keep it unchanged without calling the model.
    calls.clear();
    var blankFirst = new Chunking(model, 7, 0.7f).processText(INSTRUCTION, "     \n\nword word");
    var blankSent = calls.stream().map(call -> call.messages().getJSONObject(0).getString("content")).toList();
    assert List.of("word", "word").equals(blankSent)
            : "%s — %s: expected the blank chunk not sent but sent %s".formatted(R2_3, R2_3.statement(), blankSent);
    assert "     \n\nWORD WORD".equals(blankFirst)
            : "%s — %s: expected the blank chunk kept unchanged but got [%s]".formatted(R2_3, R2_3.statement(), blankFirst);

    // R2.4 — When a chunk is processed, the BC shall show its position and the chunk count as progress.
    var shown = stdout(() -> new Chunking(model, 10, 0.7f).processText(INSTRUCTION, TEXT));
    assert shown.contains("1/3") && shown.contains("2/3") && shown.contains("3/3")
            && shown.indexOf("1/3") < shown.indexOf("2/3") && shown.indexOf("2/3") < shown.indexOf("3/3")
            : "%s — %s: expected 1/3, 2/3 and 3/3 shown in order but showed: %s".formatted(R2_4, R2_4.statement(), shown);

    // R2.5 — When a chunk is processed, the BC shall record an event with its position, the chunk
    // count, its input and output characters and the outcome.
    var recorded = new CopyOnWriteArrayList<RecordedEvent>();
    var latch = new CountDownLatch(3);
    try (var stream = new RecordingStream()) {
        stream.enable(ChunkProcessedEvent.NAME);
        stream.onEvent(ChunkProcessedEvent.NAME, event -> {
            recorded.add(event);
            latch.countDown();
        });
        stream.startAsync();
        Thread.sleep(1_000);

        new Chunking(model, 10, 0.7f).processText(INSTRUCTION, TEXT);

        assert latch.await(10, TimeUnit.SECONDS)
                : "%s — %s: expected three events but %d arrived".formatted(R2_5, R2_5.statement(), recorded.size());
    }
    var events = recorded.stream()
            .map(event -> "%d/%d %d->%d %s".formatted(event.getInt("position"), event.getInt("count"),
                    event.getInt("inputCharacters"), event.getInt("outputCharacters"), event.getString("outcome")))
            .sorted()
            .toList();
    var expected = List.of("1/3 10->10 processed", "2/3 6->6 processed", "3/3 5->5 processed");
    assert expected.equals(events)
            : "%s — %s: expected %s but recorded %s".formatted(R2_5, R2_5.statement(), expected, events);
}

static LLMResponse text(String reply) {
    return new LLMResponse(new JSONArray().put(new JSONObject().put("type", "text").put("text", reply)), "end_turn");
}

static String stdout(Runnable action) {
    var original = System.out;
    var buffer = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    try {
        action.run();
    } finally {
        System.setOut(original);
    }
    return buffer.toString(StandardCharsets.UTF_8);
}
