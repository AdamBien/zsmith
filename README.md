# zsmith

![zsmith](zsmith.png)

AI agent framework for Claude with tool execution support.

## Usage

```java
var agent = new Agent("You are a helpful assistant.")
        .withTool(new CalculatorTool())
        .withTool(new CurrentTimeTool());

var response = agent.chat("What is 42 * 17?");
```

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
