package airhacks.zsmith.agent.boundary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;

import airhacks.zcfg.ZCfg;
import airhacks.zsmith.agent.entity.Memory;
import airhacks.zsmith.agent.entity.Message;
import airhacks.zsmith.agent.entity.Tool;
import airhacks.zsmith.agent.entity.ToolResult;
import airhacks.zsmith.agent.entity.ToolUse;
import airhacks.zsmith.claude.control.Claude;


public class Agent {

    static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant.";
    static final int DEFAULT_MAX_ITERATIONS = 10;
    static final float DEFAULT_TEMPERATURE = 0.7f;

    static{
        ZCfg.load("zsmith");
    }

    String systemPrompt;
    Memory memory;
    Map<String, Tool> tools;
    int maxIterations;
    float temperature;

    public Agent() {
        this(DEFAULT_SYSTEM_PROMPT);
    }

    public Agent(String systemPrompt) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : DEFAULT_SYSTEM_PROMPT;
        this.memory = new Memory();
        this.tools = new HashMap<>();
        this.maxIterations = DEFAULT_MAX_ITERATIONS;
        this.temperature = DEFAULT_TEMPERATURE;
    }

    public void registerTool(Tool tool) {
        this.tools.put(tool.name(), tool);
    }

    public String systemPrompt() {
        return this.systemPrompt;
    }

    public Memory memory() {
        return this.memory;
    }

    public Map<String, Tool> tools() {
        return Map.copyOf(this.tools);
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
