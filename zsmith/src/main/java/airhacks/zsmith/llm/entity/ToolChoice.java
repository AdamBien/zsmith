package airhacks.zsmith.llm.entity;

import airhacks.zsmith.configuration.control.ZCfg;

/// Whether the model may answer with prose or has to call a tool. Both wire formats can
/// demand a tool call, in shapes the providers render themselves: Anthropic sends
/// `"tool_choice": {"type": "any"}`, the OpenAI Chat Completions surface sends
/// `"tool_choice": "required"`.
///
/// - [Forcing tool use](https://docs.claude.com/en/docs/agents-and-tools/tool-use/implement-tool-use)
/// - [OpenAI chat completions reference](https://platform.openai.com/docs/api-reference/chat/create)
public enum ToolChoice {

    auto,
    required;

    public static final String REQUIRE_FIRST_KEY = "llm.require.first.tool.call";

    /// Demanding a tool call on every turn would remove the only way an agent loop ends —
    /// a reply that uses no tool — so the demand is limited to the opening turn. That is
    /// where it is needed: an agent whose first move is meant to be a question dies
    /// silently when the model writes the question as prose instead of asking it.
    ///
    /// On by default, because the two failure modes are not symmetric. Without it, a model
    /// that answers instead of calling a tool ends the session in a way that reads as a
    /// crash. With it, an endpoint that does not accept `tool_choice` answers with an error
    /// naming the field, and [#REQUIRE_FIRST_KEY] turns it off. Requests that carry no tools
    /// never reach this decision.
    ///
    /// Two openings are left to the model even so:
    ///
    /// - A continued conversation — a sub-agent delegated to a second time, a session on its
    ///   next message. The tool choice is part of what the prompt cache is keyed on, so
    ///   demanding a tool for one turn re-writes every message before it at write price.
    /// - An agent with no working tool. Left with a side channel only, the demand is met
    ///   by reporting an improvement nobody noticed, and the answer costs a second turn.
    ///
    /// - [What invalidates the cache](https://docs.claude.com/en/docs/build-with-claude/prompt-caching)
    public static ToolChoice forTurn(int iteration, boolean continued, boolean workingTools) {
        var opening = iteration == 0 && !continued && workingTools;
        var required = opening && ZCfg.bool(REQUIRE_FIRST_KEY, true);
        return required ? ToolChoice.required : auto;
    }
}
