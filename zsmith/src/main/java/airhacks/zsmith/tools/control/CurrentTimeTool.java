package airhacks.zsmith.tools.control;

import airhacks.zsmith.tools.boundary.Tool;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public interface CurrentTimeTool {

    DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static Tool create() {
        return Tool.of(
                "current_time",
                "Returns the current date and time",
                Tool.emptySchema(),
                input -> LocalDateTime.now().format(FORMAT));
    }
}
