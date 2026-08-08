package airhacks.zsmith.improvements.control;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.improvements.boundary.ImprovementLog;
import airhacks.zsmith.improvements.entity.ArtifactKind;
import airhacks.zsmith.improvements.entity.Improvement;
import airhacks.zsmith.tools.control.ToolHandler;

public class ReportImprovementTool implements ToolHandler {

    private final ImprovementLog log;

    public ReportImprovementTool(ImprovementLog log) {
        this.log = log;
    }

    @Override
    public String toolName() {
        return "report_improvement";
    }

    @Override
    public String description() {
        return """
                Reports a gap in your own instructions — the system prompt, a skill, or a \
                tool description — for a human to read and act on later. \
                Call it at the moment an instruction turns out to be ambiguous, missing, \
                contradictory, or wrong for the situation you are in, while you still have \
                the input that exposed it. \
                This is not a journal: never report that a task went well, that work was \
                completed, or what the user asked for. When nothing was unclear, do not call \
                this tool at all. \
                Report what the instruction failed to say and the concrete input that \
                exposed it; proposing a rewrite is optional and secondary, because you \
                cannot see how a different instruction would have played out. \
                Nothing you report changes your behaviour, in this session or a later one — \
                a human reads and applies it.""";
    }

    enum Field { artifact, name, observation, trigger, suggestion }

    @Override
    public JSONObject inputSchema() {
        return ToolHandler.schema(
                Prop.stringEnum(Field.artifact, "Which instruction fell short: 'prompt' for your system prompt, 'skill' for a loaded skill, 'tool' for a tool description",
                        "prompt", "skill", "tool"),
                Prop.string(Field.observation, "What the instruction failed to say, said ambiguously, or got wrong — not what you did about it"),
                Prop.string(Field.trigger, "The concrete request, input or situation that exposed the gap"),
                Prop.string(Field.name, "Name of the skill or tool. Use 'system' for the system prompt.").optional(),
                Prop.string(Field.suggestion, "Optional concrete rewrite, only when you have one worth reading").optional()
        );
    }

    @Override
    public String execute(JSONObject input) {
        var artifact = ArtifactKind.fromString(input.optString(Field.artifact.name(), null));
        var name = input.optString(Field.name.name(), Improvement.SYSTEM_PROMPT);
        var observation = input.optString(Field.observation.name(), null);
        var trigger = input.optString(Field.trigger.name(), null);
        var suggestion = input.optString(Field.suggestion.name(), "");
        try {
            if (this.log.report(Improvement.of(artifact, name, observation, trigger, suggestion))) {
                return "Reported for review.";
            }
            return "Already reported, nothing recorded.";
        } catch (IllegalArgumentException e) {
            return "Not recorded: " + e.getMessage();
        }
    }
}
