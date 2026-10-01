package airhacks.zsmith.improvements.entity;

import java.util.Arrays;
import java.util.stream.Collectors;

/// What an agent is instructed by, and therefore what a report can be about.
public enum ArtifactKind {

    prompt,
    skill,
    tool;

    public static ArtifactKind fromString(String text) {
        if (text == null) {
            return null;
        }
        try {
            return valueOf(text.toLowerCase());
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("unknown artifact '%s', expected one of %s".formatted(text, names()));
        }
    }

    public static String names() {
        return Arrays.stream(values())
                .map(ArtifactKind::name)
                .collect(Collectors.joining(", "));
    }
}
