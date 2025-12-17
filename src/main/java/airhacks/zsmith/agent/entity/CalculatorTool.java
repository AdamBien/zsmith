package airhacks.zsmith.agent.entity;

import org.json.JSONArray;
import org.json.JSONObject;

public class CalculatorTool implements Tool {

    @Override
    public String name() {
        return "calculator";
    }

    @Override
    public String description() {
        return "Performs basic arithmetic operations: add, subtract, multiply, divide";
    }

    @Override
    public JSONObject inputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("operation", new JSONObject()
                                .put("type", "string")
                                .put("enum", new JSONArray().put("add").put("subtract").put("multiply").put("divide"))
                                .put("description", "The arithmetic operation to perform"))
                        .put("a", new JSONObject()
                                .put("type", "number")
                                .put("description", "First operand"))
                        .put("b", new JSONObject()
                                .put("type", "number")
                                .put("description", "Second operand")))
                .put("required", new JSONArray().put("operation").put("a").put("b"));
    }

    @Override
    public String execute(JSONObject input) {
        var operation = input.getString("operation");
        var a = input.getDouble("a");
        var b = input.getDouble("b");

        var result = switch (operation) {
            case "add" -> a + b;
            case "subtract" -> a - b;
            case "multiply" -> a * b;
            case "divide" -> {
                if (b == 0) throw new ArithmeticException("Division by zero");
                yield a / b;
            }
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };

        return String.valueOf(result);
    }
}
