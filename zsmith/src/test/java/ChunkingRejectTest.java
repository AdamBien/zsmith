import static airhacks.zsmith.chunking.Requirement.Rn.*;

import java.util.concurrent.atomic.AtomicInteger;

import airhacks.zsmith.chunking.Requirement;
import airhacks.zsmith.chunking.boundary.Chunking;
import airhacks.zsmith.chunking.control.Model;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.llm.entity.LLMResponse;

/// Traces chunking spec R4.1, R4.2, R4.3, R4.4, R4.5, R4.6 — see src/main/java/airhacks/zsmith/chunking/package-info.java

/// Three chunks at a budget of four: "aaaa", "bbbb", "cccc".
static final String TEXT = "aaaa bbbb cccc";

/// A request the BC turns away before the model is called; a null budget leaves it to the instance.
record Rejection(Requirement.Rn req, String description, String instruction, String text, Integer budget) {}

/// A model misbehaving on the second chunk, and what the failure has to say.
record Failure(Requirement.Rn req, String description, Supplier<LLMResponse> secondReply, String reason) {}

/// Text the BC answers unchanged without calling the model.
record Unchanged(Requirement.Rn req, String description, String text) {}

void main() {
    ZCfg.loadBaseConfig("zsmith-test-chunking-" + ProcessHandle.current().pid());

    var rejections = List.of(
            new Rejection(R4_1, "a missing instruction", null, TEXT, null),
            new Rejection(R4_1, "an empty instruction", "", TEXT, null),
            new Rejection(R4_1, "a blank instruction", " \n ", TEXT, 4),
            new Rejection(R4_2, "a zero budget", "Repeat.", TEXT, 0),
            new Rejection(R4_2, "a negative budget", "Repeat.", TEXT, -5));
    for (var c : rejections) {
        var calls = new AtomicInteger();
        var chunking = new Chunking(counting(calls), 4, 0.1f);
        try {
            if (c.budget() == null) {
                chunking.processText(c.instruction(), c.text());
            } else {
                chunking.processText(c.instruction(), c.text(), c.budget());
            }
            throw new AssertionError("%s — %s — %s: expected the request rejected but it was processed"
                    .formatted(c.req(), c.req().statement(), c.description()));
        } catch (IllegalArgumentException expected) {
            assert calls.get() == 0 : "%s — %s — %s: expected no model call but made %d"
                    .formatted(c.req(), c.req().statement(), c.description(), calls.get());
        }
    }

    var unchanged = new ArrayList<Unchanged>();
    unchanged.add(new Unchanged(R4_3, "a missing text", null));
    unchanged.add(new Unchanged(R4_3, "an empty text", ""));
    unchanged.add(new Unchanged(R4_3, "a whitespace-only text", "  \n\n\t "));
    for (var c : unchanged) {
        var calls = new AtomicInteger();
        var answered = new Chunking(counting(calls), 4, 0.1f).processText("Repeat.", c.text());
        assert Objects.equals(c.text(), answered) && calls.get() == 0
                : "%s — %s — %s: expected [%s] unchanged with no model call but got [%s] after %d calls"
                        .formatted(c.req(), c.req().statement(), c.description(), c.text(), answered, calls.get());
    }

    var failures = List.of(
            new Failure(R4_4, "the model fails", () -> {
                throw new IllegalStateException("fm server error 500: overloaded");
            }, "overloaded"),
            new Failure(R4_5, "the reply is cut off", () -> reply("BBB", Model.CUT_OFF), null),
            new Failure(R4_6, "the reply is empty", () -> reply("", "end_turn"), null),
            new Failure(R4_6, "the reply is whitespace", () -> reply(" \n ", "end_turn"), null));
    for (var c : failures) {
        var calls = new AtomicInteger();
        Model model = (system, messages, tools, temperature, toolChoice) -> calls.incrementAndGet() == 2
                ? c.secondReply().get()
                : reply(messages.getJSONObject(0).getString("content"), "end_turn");
        try {
            var answered = new Chunking(model, 4, 0.1f).processText("Repeat.", TEXT);
            throw new AssertionError("%s — %s — %s: expected the whole text failed but got [%s]"
                    .formatted(c.req(), c.req().statement(), c.description(), answered));
        } catch (IllegalStateException failed) {
            var message = failed.getMessage();
            assert message.contains("chunk 2 ") && (c.reason() == null || message.contains(c.reason()))
                    : "%s — %s — %s: expected the failure to name chunk 2%s but got: %s"
                            .formatted(c.req(), c.req().statement(), c.description(),
                                    c.reason() == null ? "" : " and " + c.reason(), message);
            assert calls.get() == 2
                    : "%s — %s — %s: expected no chunk sent after the failed one but made %d calls"
                            .formatted(c.req(), c.req().statement(), c.description(), calls.get());
        }
    }
}

static Model counting(AtomicInteger calls) {
    return (system, messages, tools, temperature, toolChoice) -> {
        calls.incrementAndGet();
        return reply(messages.getJSONObject(0).getString("content"), "end_turn");
    };
}

static LLMResponse reply(String text, String stopReason) {
    return new LLMResponse(new JSONArray().put(new JSONObject().put("type", "text").put("text", text)), stopReason);
}
