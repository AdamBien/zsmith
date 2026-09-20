package airhacks.zsmith.episodicmemory.control;

import java.util.List;
import java.util.stream.Collectors;

import airhacks.zsmith.episodicmemory.entity.Episode;

/// The shape recalled memories take on the way back to a model: one line each, the
/// time first so the model can weigh how current a memory is, and one phrase for
/// "nothing here" whichever way the memories were looked for.
public interface Memories {

    String NONE_FOUND = "No memories found.";

    static String format(List<Episode> episodes) {
        if (episodes.isEmpty()) {
            return NONE_FOUND;
        }
        return episodes.stream()
                .map(Memories::line)
                .collect(Collectors.joining("\n"));
    }

    static String line(Episode episode) {
        var line = "[%s] %s".formatted(episode.timestamp(), episode.content());
        if (episode.type() == null) {
            return line;
        }
        return line + " (type: %s)".formatted(episode.type().name());
    }
}
