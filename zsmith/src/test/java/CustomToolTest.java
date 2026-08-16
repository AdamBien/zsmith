import airhacks.zsmith.agent.boundary.Agent;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.tools.boundary.Tool;
import airhacks.zsmith.tools.control.RecordTool;
import airhacks.zsmith.tools.entity.Describe;

/// Traces tools spec R1.6, R1.7 — see src/main/java/airhacks/zsmith/tools/package-info.java

record EchoTool() implements Tool {

    enum Field { text }

    @Override
    public String toolName() {
        return "echo";
    }

    @Override
    public String description() {
        return "Returns the supplied text unchanged";
    }

    @Override
    public JSONObject inputSchema() {
        return Tool.schema(Tool.Prop.string(Field.text, "the text to echo"));
    }

    @Override
    public String execute(JSONObject input) {
        return input.getString(Field.text.name());
    }
}

@Describe("Waves farewell by name")
record FarewellTool(@Describe("the person to wave at") String name) implements RecordTool {

    @Override
    public String execute() {
        return "farewell " + this.name;
    }
}

void main() {
    customToolIsInterchangeableWithBuiltInHandlers();
    recordToolEquipsThroughTheContract();
}

// R1.6 — The BC shall publish the handler contract at its boundary so a tool implemented
// outside the framework is interchangeable with every built-in handler.
void customToolIsInterchangeableWithBuiltInHandlers() {
    var agent = new Agent("custom-tool-r16", "prompt R1.6").withTool(new EchoTool());
    var equipped = agent.tools().get("echo");
    if (equipped == null)
        throw new AssertionError("R1.6 — expected custom tool equipped by name but got: " + agent.tools().keySet());
    var result = equipped.execute(new JSONObject().put(EchoTool.Field.text.name(), "ping"));
    if (!"ping".equals(result))
        throw new AssertionError("R1.6 — expected custom tool executed through the catalog but got: " + result);
}

// R1.7 — When an annotated record is registered, the BC shall adopt it as a handler
// interchangeable with every built-in one.
void recordToolEquipsThroughTheContract() {
    var agent = new Agent("record-tool-r17", "prompt R1.7").withTool(Tool.of(FarewellTool.class));
    var equipped = agent.tools().get("farewell");
    if (equipped == null)
        throw new AssertionError("R1.7 — expected record tool equipped under its derived name but got: " + agent.tools().keySet());
    var result = equipped.execute(new JSONObject().put("name", "duke"));
    if (!"farewell duke".equals(result))
        throw new AssertionError("R1.7 — expected record tool executed with coerced arguments but got: " + result);
}
