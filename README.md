# zsmith

![zsmith](zsmith.png)

AI agent framework for Claude with tool execution support.

## Usage

```java
var agent = new Agent("calculator", "You are a helpful assistant.")
        .withTool(new CalculatorTool())
        .withTool(new CurrentTimeTool());

var response = agent.chat("What is 42 * 17?");
```

## Configuration

Properties are loaded in order (each layer overrides the previous):

1. `~/.zsmith/app.properties` — global defaults
2. `./app.properties` — local project defaults
3. `~/.zsmith/[agentName]/app.properties` — global agent-specific
4. `./[agentName]/app.properties` — local agent-specific
5. System properties — highest priority

Only keys present in later files override earlier values; other keys are preserved.

## Custom Tools

Implement the `Tool` interface:

```java
public class MyTool implements Tool {

    public String name() {
        return "my_tool";
    }

    public String description() {
        return "Does something useful";
    }

    public String inputSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "param": { "type": "string", "description": "Parameter description" }
                },
                "required": ["param"]
            }
            """;
    }

    public String execute(JSONObject input) {
        return "Result: " + input.getString("param");
    }
}
```
