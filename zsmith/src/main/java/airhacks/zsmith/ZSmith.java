package airhacks.zsmith;

import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.tools.control.CalculatorTool;
import airhacks.zsmith.tools.control.CurrentTimeTool;

public interface ZSmith {

    static final System.Logger LOGGER = System.getLogger(ZSmith.class.getName());

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

        LOGGER.log(System.Logger.Level.INFO, "Agent initialized with calculator and current_time tools");

        var question = "What is 42 multiplied by 17? Also, what time is it now?";
        LOGGER.log(System.Logger.Level.INFO, "User: {0}", question);

        var response = agent.chat(question);
        LOGGER.log(System.Logger.Level.INFO, "Agent: {0}", response);
    }
}
