package airhacks.zsmith;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.agent.entity.CalculatorTool;
import airhacks.zsmith.agent.entity.CurrentTimeTool;

public interface ZSmith {

    static void main(String...args) {
        var systemPrompt = """
                You are a helpful assistant with access to tools.
                Use the calculator tool for math operations.
                Use the current_time tool to get the current date and time.
                Be concise in your responses.
                """;

        var agent = new Agent(systemPrompt);
        agent.registerTool(new CalculatorTool());
        agent.registerTool(new CurrentTimeTool());

        System.out.println("Agent initialized with calculator and current_time tools");
        System.out.println("---");

        var question = "What is 42 multiplied by 17? Also, what time is it now?";
        System.out.println("User: " + question);
        System.out.println("---");

        var response = agent.chat(question);
        System.out.println("Agent: " + response);
    }
}
