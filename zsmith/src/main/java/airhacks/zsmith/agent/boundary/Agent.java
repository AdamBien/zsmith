package airhacks.zsmith.agent.boundary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;

import airhacks.zcfg.ZCfg;
import airhacks.zsmith.agent.entity.Memory;
import airhacks.zsmith.agent.entity.Message;
import airhacks.zsmith.claude.control.Claude;
import airhacks.zsmith.tools.control.Tool;
import airhacks.zsmith.tools.entity.ToolResult;
import airhacks.zsmith.tools.entity.ToolUse;


public record Agent(String systemPrompt, Memory memory, Map<String, Tool> tools, int maxIterations, float temperature) {

    static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";
    static final int DEFAULT_MAX_ITERATIONS = 10;
    static final float DEFAULT_TEMPERATURE = 0.7f;

    static {
        ZCfg.load("zsmith");
        var apiKey = ZCfg.string("anthropic.api.key");
        var version = ZCfg.string("anthropic.version");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Missing configuration: 'anthropic.api.key' not set");
            System.exit(1);
        }
        if (version == null || version.isBlank()) {
            System.err.println("Missing configuration: 'anthropic.version' not set");
            System.exit(1);
        }
    }

    public Agent(String systemPrompt) {
        this(
            systemPrompt != null ? systemPrompt : DEFAULT_SYSTEM_PROMPT,
            new Memory(),
            new HashMap<>(),
            DEFAULT_MAX_ITERATIONS,
            DEFAULT_TEMPERATURE
        );
    }

    public Agent() {
        this(DEFAULT_SYSTEM_PROMPT);
    }

    public Agent withTool(Tool tool) {
        this.tools.put(tool.name(), tool);
        return this;
    }

    public Agent withMaxIterations(int maxIterations) {
        return new Agent(this.systemPrompt, this.memory, this.tools, maxIterations, this.temperature);
    }

    public Agent withTemperature(float temperature) {
        return new Agent(this.systemPrompt, this.memory, this.tools, this.maxIterations, temperature);
    }


    JSONArray toolDefinitions() {
        var array = new JSONArray();
        this.tools.values().stream()
                .map(Tool::toToolDefinition)
                .forEach(array::put);
        return array;
    }

    ToolResult executeTool(ToolUse toolUse) {
        var tool = this.tools.get(toolUse.name());
        if (tool == null) {
            return ToolResult.error(toolUse.id(), "Tool not available: " + toolUse.name());
        }
        try {
            var result = tool.execute(toolUse.input());
            return ToolResult.success(toolUse.id(), result);
        } catch (Exception e) {
            return ToolResult.error(toolUse.id(), e.getMessage());
        }
    }

    public String chat(String userMessage) {
        this.memory.addUserMessage(userMessage);

        for (int iteration = 0; iteration < this.maxIterations; iteration++) {
            var response = Claude.invoke(
                    this.systemPrompt,
                    this.memory.toJSON(),
                    toolDefinitions(),
                    this.temperature
            );

            var content = response.getJSONArray("content");
            var stopReason = response.optString("stop_reason", "end_turn");

            var textParts = extractTextContent(content);
            var toolUses = extractToolUses(content);

            if (toolUses.isEmpty() || !"tool_use".equals(stopReason)) {
                if (!textParts.isEmpty()) {
                    var assistantResponse = String.join("\n", textParts);
                    this.memory.addAssistantMessage(assistantResponse);
                    return assistantResponse;
                }
                return "";
            }

            addAssistantContentToMemory(content);

            var toolResults = new JSONArray();
            for (var toolUse : toolUses) {
                var result = executeTool(toolUse);
                toolResults.put(result.toContentBlock());
            }

            this.memory.addMessage(Message.withContentBlocks("user", toolResults));
        }

        return "Max iterations reached";
    }

    public void clearMemory() {
        this.memory.clear();
    }

    List<String> extractTextContent(JSONArray content) {
        var texts = new ArrayList<String>();
        for (int i = 0; i < content.length(); i++) {
            var block = content.getJSONObject(i);
            if ("text".equals(block.optString("type"))) {
                texts.add(block.getString("text"));
            }
        }
        return texts;
    }

    List<ToolUse> extractToolUses(JSONArray content) {
        var toolUses = new ArrayList<ToolUse>();
        for (int i = 0; i < content.length(); i++) {
            var block = content.getJSONObject(i);
            if (ToolUse.isToolUse(block)) {
                toolUses.add(ToolUse.fromJSON(block));
            }
        }
        return toolUses;
    }

    void addAssistantContentToMemory(JSONArray content) {
        this.memory.addMessage(Message.withContentBlocks("assistant", content));
    }
}
