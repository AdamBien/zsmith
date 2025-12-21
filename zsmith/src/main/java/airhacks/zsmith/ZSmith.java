package airhacks.zsmith;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.logging.control.Log;
import airhacks.zsmith.tools.control.CalculatorTool;
import airhacks.zsmith.tools.control.CurrentTimeTool;

public interface ZSmith {

    static void main(String...args) {
        var systemPrompt = """
                You are a helpful assistant with access to tools.
                Use the calculator tool for math operations.
                Use the current_time tool to get the current date and time.
                Be concise in your responses.
                """;

        var agent = new Agent(systemPrompt)
                .withTool(new CalculatorTool())
                .withTool(new CurrentTimeTool());

        Log.INFO.out("Agent initialized with calculator and current_time tools");

        var question = "What is 42 multiplied by 17? Also, what time is it now?";
        Log.PROMPT.out("User: " + question);

        var response = agent.chat(question);
        Log.ANSWER.out("Agent: " + response);
    }
}
