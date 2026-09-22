import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.Properties;

import airhacks.zsmith.claude.boundary.ClaudeModels;
import airhacks.zsmith.claude.control.Claude.Capability;
import airhacks.zsmith.claude.control.Claude.Models;
import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

/// Traces claude spec R1.1–R1.4, R2.1–R2.4, R3.1–R3.3 and R4.1–R4.4 — see
/// src/main/java/airhacks/zsmith/claude/package-info.java
///
/// Every case reloads the configuration from a cleared baseline, so the model under test is
/// whatever the case configures rather than the one the transport picked at startup.

static final String APP = "zsmith-test-claude-models-" + ProcessHandle.current().pid();

void main() {
    reload(_ -> {});

    offersClaudeOpus5();
    selectsAModelByName();
    namesTheModelForTheProvider();
    carriesOnlyWhatTheModelAccepts();

    System.out.println("ClaudeModelSelectionTest passed");
}

// R1.1 — The BC shall offer Claude Opus 5 as a selectable model that accepts effort and adaptive
// thinking and no temperature.
// R1.2 — The BC shall allow Claude Opus 5 up to 64,000 output tokens per response.
// R1.3 — The BC shall name Claude Opus 4.8 as the model to retry with when Claude Opus 5 is overloaded.
// R1.4 — While neither a model is configured nor one is requested, the BC shall select Claude Opus 5.
void offersClaudeOpus5() {
    reload(props -> props.setProperty("claude.model", "claude-opus-5"));
    var opus5 = ClaudeModels.selectModel();
    assert "claude-opus-5".equals(opus5.modelName()) : "R1.1 — expected claude-opus-5, got " + opus5.modelName();
    assert opus5.supports(Capability.EFFORT) : "R1.1 — Claude Opus 5 accepts effort";
    assert opus5.supports(Capability.ADAPTIVE_THINKING) : "R1.1 — Claude Opus 5 accepts adaptive thinking";
    assert !opus5.supports(Capability.TEMPERATURE) : "R1.1 — Claude Opus 5 accepts no temperature";
    assert opus5.maxTokens() == 64_000 : "R1.2 — expected 64000 output tokens, got " + opus5.maxTokens();
    assert "claude-opus-4-8".equals(opus5.fallbackModelName()) : "R1.3 — expected claude-opus-4-8 as retry model, got " + opus5.fallbackModelName();

    reload(_ -> {});
    var selected = ClaudeModels.selectModel();
    assert selected == Models.CLAUDE_5_OPUS : "R1.4 — nothing configured or requested selects Claude Opus 5, got " + selected;
}

// R2.1 — When a model is configured, the BC shall select the catalog model it names ahead of a
// requested name and the default.
// R2.2 — When a partial name matches exactly one catalog model, the BC shall select it, disregarding case.
// R2.3 — If a partial name matches several catalog models, then the BC shall select the most
// recently released one.
// R2.4 — If the configured name matches no catalog model, then the BC shall select from the
// requested name, and failing that the default.
void selectsAModelByName() {
    record Case(String id, String configured, String requested, Models expected) {}
    var cases = List.of(
        new Case("R2.1", "claude-opus-4-7", "4-6", Models.CLAUDE_47_OPUS),
        new Case("R2.2", null, "4-8", Models.CLAUDE_48_OPUS),
        new Case("R2.2", "OPUS-5", null, Models.CLAUDE_5_OPUS),
        new Case("R2.3", "opus", null, Models.CLAUDE_5_OPUS),
        new Case("R2.3", null, "claude", Models.CLAUDE_5_OPUS),
        new Case("R2.4", "claude-haiku-4-5", "4-6", Models.CLAUDE_46_OPUS),
        new Case("R2.4", "claude-haiku-4-5", null, Models.CLAUDE_5_OPUS)
    );
    for (var c : cases) {
        reload(props -> {
            if (c.configured() != null) props.setProperty("claude.model", c.configured());
            if (c.requested() != null) props.setProperty("model", c.requested());
        });
        var selected = ClaudeModels.selectModel();
        assert selected == c.expected()
            : "%s — configured=%s requested=%s: expected %s, got %s".formatted(c.id(), c.configured(), c.requested(), c.expected(), selected);
    }
}

// R3.1 — The BC shall send the selected model under its catalog name.
// R3.2 — While Bedrock is the provider, when the name carries no namespace, the BC shall prefix it
// with the Anthropic namespace.
// R3.3 — If a configured name matches no catalog model, then the BC shall send the configured name as given.
void namesTheModelForTheProvider() {
    record Case(String id, String provider, String configured, Models model, String expected) {}
    var cases = List.of(
        new Case("R3.1", "claude", null, Models.CLAUDE_5_OPUS, "claude-opus-5"),
        new Case("R3.2", "bedrock", null, Models.CLAUDE_5_OPUS, "anthropic.claude-opus-5"),
        new Case("R3.2", "bedrock", "anthropic.claude-opus-5", Models.CLAUDE_5_OPUS, "anthropic.claude-opus-5"),
        new Case("R3.3", "claude", "claude-haiku-4-5", Models.CLAUDE_5_OPUS, "claude-haiku-4-5")
    );
    for (var c : cases) {
        reload(props -> {
            props.setProperty("llm.provider", c.provider());
            if (c.configured() != null) props.setProperty("claude.model", c.configured());
        });
        var name = ClaudeModels.resolveModelName(c.model());
        assert c.expected().equals(name)
            : "%s — provider=%s configured=%s: expected %s, got %s".formatted(c.id(), c.provider(), c.configured(), c.expected(), name);
    }
}

// R4.1 — The BC shall include temperature only for a model that accepts it.
// R4.2 — The BC shall include effort only for a model that accepts it.
// R4.3 — The BC shall include a thinking mode only for a model that accepts it.
// R4.4 — If thinking is configured off while effort is configured above high for Claude Opus 5,
// then the BC shall send the request without a thinking mode and warn that adaptive thinking applies.
void carriesOnlyWhatTheModelAccepts() {
    record Case(String id, Models model, String thinking, String effort, String key, boolean present) {}
    var cases = List.of(
        new Case("R4.1", Models.NVIDIA_NEMOTRON_SUPER_3_120B, null, null, "temperature", true),
        new Case("R4.1", Models.CLAUDE_5_OPUS, null, null, "temperature", false),
        new Case("R4.2", Models.CLAUDE_5_OPUS, null, "high", "output_config", true),
        new Case("R4.2", Models.NVIDIA_NEMOTRON_SUPER_3_120B, null, "high", "output_config", false),
        new Case("R4.3", Models.CLAUDE_5_OPUS, "adaptive", null, "thinking", true),
        new Case("R4.3", Models.NVIDIA_NEMOTRON_SUPER_3_120B, "adaptive", null, "thinking", false),
        new Case("R4.4", Models.CLAUDE_5_OPUS, "disabled", "high", "thinking", true),
        new Case("R4.4", Models.CLAUDE_5_OPUS, "disabled", "xhigh", "thinking", false),
        new Case("R4.4", Models.CLAUDE_5_OPUS, "disabled", "max", "thinking", false),
        new Case("R4.4", Models.CLAUDE_48_OPUS, "disabled", "max", "thinking", true)
    );
    for (var c : cases) {
        reload(props -> {
            if (c.thinking() != null) props.setProperty("claude.thinking", c.thinking());
            if (c.effort() != null) props.setProperty("claude.effort", c.effort());
        });
        var request = compose(c.model());
        assert request.has(c.key()) == c.present()
            : "%s — %s thinking=%s effort=%s: expected %s %s, request was %s"
                .formatted(c.id(), c.model(), c.thinking(), c.effort(), c.key(), c.present() ? "present" : "absent", request);
    }

    reload(props -> {
        props.setProperty("claude.thinking", "disabled");
        props.setProperty("claude.effort", "xhigh");
    });
    var console = captureConsole(() -> compose(Models.CLAUDE_5_OPUS));
    assert console.contains("adaptive thinking") : "R4.4 — expected a warning that adaptive thinking applies, console was: " + console;
}

JSONObject compose(Models model) {
    var messages = new JSONArray().put(new JSONObject().put("role", "user").put("content", "hi"));
    return ClaudeModels.composeRequest(model, "system", messages, 0.5f);
}

String captureConsole(Runnable action) {
    var buffer = new ByteArrayOutputStream();
    var original = System.out;
    System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    try {
        action.run();
    } finally {
        System.setOut(original);
    }
    return buffer.toString(StandardCharsets.UTF_8);
}

/// Repopulates ZCfg from system properties only — every key this BC reads is cleared first so
/// each case starts from a known-empty baseline, then the mutator sets just what it needs.
void reload(Consumer<Properties> mutator) {
    for (var key : List.of("llm.provider", "claude.model", "model", "claude.thinking", "claude.effort")) {
        System.clearProperty(key);
    }
    mutator.accept(System.getProperties());
    ZCfg.loadBaseConfig(APP);
}
