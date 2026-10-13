import java.time.Duration;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.fm.boundary.Fm;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.llm.entity.ToolChoice;

/// A process that sends one turn and ends without stopping the server, for the fm BC's
/// R4.10. Not a test itself — launched by FmServerTest.java.
///
/// Usage: java --source 25 --class-path <zsmith.jar> TurnThenEnd.java "<fm launch command>"

void main(String[] args) {
    ZCfg.loadBaseConfig("zsmith-test-fm-" + ProcessHandle.current().pid());
    var messages = new JSONArray().put(new JSONObject().put("role", "user").put("content", "hello"));
    Fm.of(args[0], Duration.ofSeconds(10)).invokeModel(null, messages, null, 0.5f, ToolChoice.auto);
}
