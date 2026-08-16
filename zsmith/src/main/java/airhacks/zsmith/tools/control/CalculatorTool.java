package airhacks.zsmith.tools.control;

import airhacks.zsmith.tools.boundary.Tool;
import airhacks.zsmith.json.JSONObject;

public interface CalculatorTool {

    enum Field { operation, a, b }

    static Tool create() {
        return Tool.of(
                "calculator",
                "Performs basic arithmetic operations: add, subtract, multiply, divide",
                Tool.schema(
                        Tool.Prop.stringEnum(Field.operation, "The arithmetic operation to perform",
                                "add", "subtract", "multiply", "divide"),
                        Tool.Prop.number(Field.a, "First operand"),
                        Tool.Prop.number(Field.b, "Second operand")),
                CalculatorTool::run);
    }

    private static String run(JSONObject input) {
        var operation = input.getString(Field.operation.name());
        var a = input.getDouble(Field.a.name());
        var b = input.getDouble(Field.b.name());

        // An LLM reading "Infinity" cannot tell it divided by zero, so the
        // degenerate case is named rather than propagated as a float value.
        if ("divide".equals(operation) && b == 0) {
            return "Error: division by zero";
        }
        return switch (operation) {
            case "add" -> String.valueOf(a + b);
            case "subtract" -> String.valueOf(a - b);
            case "multiply" -> String.valueOf(a * b);
            case "divide" -> String.valueOf(a / b);
            default -> "Error: unsupported operation: " + operation;
        };
    }
}
