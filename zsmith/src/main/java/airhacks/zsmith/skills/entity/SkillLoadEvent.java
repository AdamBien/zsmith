package airhacks.zsmith.skills.entity;

import java.nio.file.Path;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import airhacks.zsmith.Concern;
import static airhacks.zsmith.Concern.Kind.OBSERVABILITY;

@Concern(OBSERVABILITY)
@Name("airhacks.zsmith.skills.Load")
@Label("Skill Load")
@Category({"zsmith", "skills"})
@Description("Single skill read from disk during SkillStore initialization")
public class SkillLoadEvent extends Event {

    @Label("Skill Name")
    public String skillName;

    @Label("Path")
    public String path;

    @Label("Content Size")
    public int contentSize;

    @Label("Outcome")
    public String outcome;

    /// The where-am-I half of the event: which file is being read. Name, size and outcome
    /// are only known once the file has been parsed.
    public static SkillLoadEvent of(Path skillFile) {
        var event = new SkillLoadEvent();
        event.path = skillFile.toString();
        return event;
    }
}
