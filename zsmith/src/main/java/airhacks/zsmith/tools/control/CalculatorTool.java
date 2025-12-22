package airhacks.zsmith.tools.control;

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
    public String inputSchema() {
        return """
                {
                    "type": "object",
                    "properties": {
                        "operation": {
                            "type": "string",
                            "enum": ["add", "subtract", "multiply", "divide"],
                            "description": "The arithmetic operation to perform"
                        },
                        "a": { "type": "number", "description": "First operand" },
                        "b": { "type": "number", "description": "Second operand" }
                    },
                    "required": ["operation", "a", "b"]
                }
                """;
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
