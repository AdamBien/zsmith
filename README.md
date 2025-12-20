# zsmith

AI agent framework for Claude with tool execution support.

## Usage

```java
var agent = new Agent("You are a helpful assistant.");
agent.registerTool(new CalculatorTool());
agent.registerTool(new CurrentTimeTool());

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

    public JSONObject inputSchema() {
        return new JSONObject()
            .put("type", "object")
            .put("properties", new JSONObject()
                .put("param", new JSONObject().put("type", "string")))
            .put("required", new JSONArray().put("param"));
    }

    public String execute(JSONObject input) {
        return "Result: " + input.getString("param");
    }
}
```
