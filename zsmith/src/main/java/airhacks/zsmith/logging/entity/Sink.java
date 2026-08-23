package airhacks.zsmith.logging.entity;

/// Where the routable channels go.
///
/// The console is the default because it is what the framework did before it could do anything
/// else — a destination nobody asked for is a destination nobody finds.
public enum Sink {

    CONSOLE, FILE, BOTH;

    /// Anything unrecognised answers the console: a typo in a destination must not silence a run.
    public static Sink of(String value) {
        if (value == null) {
            return CONSOLE;
        }
        return switch (value.strip().toLowerCase()) {
            case "file" -> FILE;
            case "both" -> BOTH;
            default -> CONSOLE;
        };
    }

    public boolean writesToFile() {
        return this != CONSOLE;
    }

    public boolean writesToConsole() {
        return this != FILE;
    }
}
