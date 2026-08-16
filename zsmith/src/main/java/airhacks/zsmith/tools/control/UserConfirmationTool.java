package airhacks.zsmith.tools.control;

import airhacks.zsmith.tools.boundary.Tool;
import java.util.function.Function;

import airhacks.zsmith.json.JSONObject;

public interface UserConfirmationTool {

    enum Field { question }

    static Tool create() {
        return create(Console::prompt);
    }

    static Tool create(Function<String, String> promptFunction) {
        return Tool.of(
                "user_confirmation",
                "Asks the user a yes/no question and returns the answer",
                Tool.schema(Tool.Prop.string(Field.question, "The yes/no question to ask the user")),
                input -> run(input, promptFunction));
    }

    private static String run(JSONObject input, Function<String, String> promptFunction) {
        if (!input.has(Field.question.name()) || input.getString(Field.question.name()).isEmpty()) {
            throw new IllegalArgumentException("Missing or empty required parameter: question");
        }
        var question = input.getString(Field.question.name());
        var prompt = question + " (yes/no): ";
        var response = promptFunction.apply(prompt);
        return switch (response.toLowerCase()) {
            case "yes", "y" -> "yes";
            default -> "no";
        };
    }
}
