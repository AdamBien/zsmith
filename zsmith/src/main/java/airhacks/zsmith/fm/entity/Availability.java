package airhacks.zsmith.fm.entity;

/// Whether the on-device model can serve, and why not when it cannot. The reason is what
/// a failed turn reports, so it is written to be read by the person who has to fix it.
public record Availability(boolean available, String reason) {

    public static Availability ofAvailable() {
        return new Availability(true, "");
    }

    public static Availability ofUnavailable(String reason) {
        return new Availability(false, reason);
    }
}
