import java.util.concurrent.atomic.AtomicReference;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.tools.boundary.ToolProfiles;
import airhacks.zsmith.tools.boundary.Tools;
import airhacks.zsmith.tools.control.CalculatorTool;
import airhacks.zsmith.tools.control.CurrentTimeTool;
import airhacks.zsmith.tools.control.RecordTool;
import airhacks.zsmith.tools.control.ToolPermission;
import airhacks.zsmith.tools.control.ToolRegistry;
import airhacks.zsmith.tools.control.UserMessageTool;
import airhacks.zsmith.tools.control.UserQuestionTool;
import airhacks.zsmith.tools.entity.Describe;

/// Traces tools spec R1.1, R1.5, R2.2, R2.4, R10.1 - R10.3, R11.1, R15.1, R15.2
/// — see src/main/java/airhacks/zsmith/tools/package-info.java

@Describe("Greets by name")
record GreetPersonTool(String name) implements RecordTool {
    @Override
    public String execute() {
        return "hello " + this.name;
    }
}

void main() {
    // ToolProfiles.fileIO and ToolPermission.resolve both read configuration
    System.clearProperty(ToolPermission.PREFIX + "default");
    airhacks.zsmith.configuration.control.ZCfg.loadBaseConfig("zsmith");

    catalogIsAddressableByName();
    profilesGroupHandlersByCapability();
    permissionDefaultsToConfirmation();
    recordToolNameIsDerivedInSnakeCase();
    arithmeticIsEvaluated();
    divisionByZeroIsReported();
    unknownOperationIsReported();
    currentTimeIsReturned();
    aMessageIsPresented();
    aQuestionReturnsTheTypedAnswer();
}

// R1.1 — The BC shall expose every ready-to-use handler as a catalog addressable by name.
void catalogIsAddressableByName() {
    for (var tool : Tools.values()) {
        if (tool.toolName() == null || tool.toolName().isBlank())
            throw new AssertionError("R1.1 — every catalog entry needs a name, missing on: " + tool);
    }
    if (ToolProfiles.all().size() != Tools.values().length)
        throw new AssertionError("R1.1 — expected the catalog to expose every handler but got: " + ToolProfiles.all().size());
}

// R1.5 — The BC shall expose curated handler groupings for user interaction, clipboard and
// file access.
void profilesGroupHandlersByCapability() {
    var userIO = ToolProfiles.userIO().stream().map(t -> t.toolName()).toList();
    if (!userIO.contains("user_message") || !userIO.contains("user_question") || !userIO.contains("user_confirmation"))
        throw new AssertionError("R1.5 — unexpected user-interaction grouping: " + userIO);

    var clipboard = ToolProfiles.clipboard().stream().map(t -> t.toolName()).toList();
    if (!clipboard.contains("read_clipboard") || !clipboard.contains("write_clipboard"))
        throw new AssertionError("R1.5 — unexpected clipboard grouping: " + clipboard);

    var fileIO = ToolProfiles.fileIO("catalog-test").stream().map(t -> t.toolName()).toList();
    if (!fileIO.contains("read_file") || !fileIO.contains("read_any_file"))
        throw new AssertionError("R1.5 — unexpected file-access grouping: " + fileIO);
}

// R2.2 — The BC shall report each handler's configured permission, and shall report
// confirmation as required when none is configured.
void permissionDefaultsToConfirmation() {
    var resolved = ToolPermission.resolve("a_tool_nobody_configured");
    if (resolved != ToolPermission.CONFIRM)
        throw new AssertionError("R2.2 — expected CONFIRM for an unconfigured tool but got: " + resolved);

    System.setProperty(ToolPermission.PREFIX + "a_configured_tool", "deny");
    airhacks.zsmith.configuration.control.ZCfg.loadBaseConfig("zsmith");
    var configured = ToolPermission.resolve("a_configured_tool");
    if (configured != ToolPermission.DENY)
        throw new AssertionError("R2.2 — expected the configured permission reported but got: " + configured);
}

// R2.4 — When a record tool is registered, the BC shall derive its name from the class name
// in snake case.
void recordToolNameIsDerivedInSnakeCase() {
    var definitions = new ToolRegistry().register(GreetPersonTool.class).toolDefinitions().toString();
    if (!definitions.contains("greet_person"))
        throw new AssertionError("R2.4 — expected the derived name 'greet_person' but got: " + definitions);
}

// R10.1 — When an operation and two operands are supplied, the BC shall return the result.
void arithmeticIsEvaluated() {
    var tool = CalculatorTool.create();
    var sum = tool.execute(new JSONObject().put("operation", "add").put("a", 2).put("b", 3));
    if (!sum.startsWith("5"))
        throw new AssertionError("R10.1 — expected 5 but got: " + sum);
    var quotient = tool.execute(new JSONObject().put("operation", "divide").put("a", 9).put("b", 3));
    if (!quotient.startsWith("3"))
        throw new AssertionError("R10.1 — expected 3 but got: " + quotient);
}

// R10.2 — If a division by zero is requested, then the BC shall report it as an error.
void divisionByZeroIsReported() {
    var result = CalculatorTool.create()
            .execute(new JSONObject().put("operation", "divide").put("a", 1).put("b", 0));
    if (!result.startsWith("Error:"))
        throw new AssertionError("R10.2 — expected an error rather than a float value but got: " + result);
}

// R10.3 — If the operation is unrecognised, then the BC shall report it as unsupported.
void unknownOperationIsReported() {
    var result = CalculatorTool.create()
            .execute(new JSONObject().put("operation", "exponentiate").put("a", 2).put("b", 8));
    if (!result.startsWith("Error:") || !result.contains("exponentiate"))
        throw new AssertionError("R10.3 — expected the unsupported operation named but got: " + result);
}

// R11.1 — The BC shall return the current date and time.
void currentTimeIsReturned() {
    var now = CurrentTimeTool.create().execute(new JSONObject());
    if (now == null || now.isBlank())
        throw new AssertionError("R11.1 — expected a timestamp but got: " + now);
    if (!now.contains(String.valueOf(java.time.Year.now().getValue())))
        throw new AssertionError("R11.1 — expected the current year in: " + now);
}

// R15.1 — When a message is supplied, the BC shall present it and confirm it was shown.
void aMessageIsPresented() {
    var presented = new AtomicReference<String>();
    var result = UserMessageTool.create(presented::set)
            .execute(new JSONObject().put("message", "build finished"));
    if (!"build finished".equals(presented.get()))
        throw new AssertionError("R15.1 — expected the message presented but got: " + presented.get());
    if (result == null || result.isBlank())
        throw new AssertionError("R15.1 — expected a confirmation that it was shown but got: " + result);
}

// R15.2 — When a question is supplied, the BC shall return the user's typed answer.
void aQuestionReturnsTheTypedAnswer() {
    var answer = UserQuestionTool.create(question -> "Bien")
            .execute(new JSONObject().put("question", "Your name?"));
    if (!"Bien".equals(answer))
        throw new AssertionError("R15.2 — expected the typed answer but got: " + answer);
}
