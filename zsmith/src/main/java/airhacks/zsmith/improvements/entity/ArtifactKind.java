package airhacks.zsmith.improvements.entity;

/// What an agent is instructed by, and therefore what a report can be about.
public enum ArtifactKind {

    prompt,
    skill,
    tool;

    public static ArtifactKind fromString(String text) {
        if (text == null) {
            return null;
        }
        return valueOf(text.toLowerCase());
    }
}
