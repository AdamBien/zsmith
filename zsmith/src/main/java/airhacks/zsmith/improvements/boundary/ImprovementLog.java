package airhacks.zsmith.improvements.boundary;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.htmldb.boundary.HtmlStore;
import airhacks.zsmith.improvements.entity.Improvement;
import airhacks.zsmith.logging.control.Log;

/// An `improvements` table in the agent's own database, alongside its memories. The
/// generated index makes the backlog a page to read rather than a file to grep.
public class ImprovementLog {

    static final String IMPROVEMENTS_TABLE = "improvements";
    static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private final List<Improvement> improvements;
    private final HtmlStore store;

    public ImprovementLog(Path databaseRoot) {
        this.store = new HtmlStore(databaseRoot);
        this.improvements = new ArrayList<>(load());
    }

    public static ImprovementLog forAgent(String agentName) {
        return new ImprovementLog(ZCfg.agentDatabase(agentName));
    }

    /// Answers false when this gap is already on the backlog, so an agent hitting the
    /// same missing instruction in every turn reports it once.
    public boolean report(Improvement improvement) {
        if (alreadyReported(improvement)) {
            Log.memory("improvement already reported, skipped: " + improvement.headline());
            return false;
        }
        this.improvements.add(improvement);
        save(improvement);
        return true;
    }

    boolean alreadyReported(Improvement improvement) {
        return this.improvements.stream().anyMatch(improvement::isSameObservation);
    }

    public List<Improvement> all() {
        return this.improvements.stream()
                .sorted(Comparator.comparing(Improvement::timestamp))
                .toList();
    }

    public void clear() {
        this.improvements.clear();
        this.store.removeTable(IMPROVEMENTS_TABLE);
    }

    void save(Improvement improvement) {
        this.store.append(IMPROVEMENTS_TABLE, keyOf(improvement), improvement.toFields());
    }

    List<Improvement> load() {
        return this.store.keys(IMPROVEMENTS_TABLE).stream()
                .map(this::read)
                .flatMap(Optional::stream)
                .toList();
    }

    Optional<Improvement> read(String key) {
        try {
            return this.store.get(IMPROVEMENTS_TABLE, key).map(entry -> Improvement.fromFields(entry.fields()));
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException e) {
            Log.warning("skipping unreadable improvement " + key + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    static String keyOf(Improvement improvement) {
        return KEY_FORMAT.format(instantOf(improvement.timestamp()));
    }

    static Instant instantOf(String timestamp) {
        if (timestamp == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException _) {
            return Instant.now();
        }
    }
}
