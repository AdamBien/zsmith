import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.fm.boundary.Fm;

/// Traces fm spec R1.1, R1.2, R1.3 — see src/main/java/airhacks/zsmith/fm/package-info.java
/// The `fm` command is src/test/java/fm/FakeFm.java; it logs how it was launched and every turn it receives.

void main() throws IOException {
    ZCfg.loadBaseConfig("zsmith-test-fm-" + ProcessHandle.current().pid());
    var log = Files.createTempFile("zunit-fm", ".log");
    try {
        // R1.1 — When the `fm` command is installed and reports the model as available, the BC shall
        // answer that the model is available.
        var available = Fm.of(fakeFm(log), Duration.ofSeconds(10)).checkAvailability();
        assert available.available() : "R1.1 — expected the model available but got: " + available;

        // R1.2 — If the `fm` command is not installed, then the BC shall answer that the model is
        // unavailable and name the missing command.
        var missing = Fm.of("/nonexistent/fm", Duration.ofSeconds(10)).checkAvailability();
        assert !missing.available() && missing.reason().contains("/nonexistent/fm")
                : "R1.2 — expected unavailable naming the missing command but got: " + missing;

        // R1.3 — If the `fm` command reports the model as unavailable, then the BC shall answer that
        // the model is unavailable together with the reason the command gave.
        var unavailable = Fm.of(fakeFm(log, "--unavailable"), Duration.ofSeconds(10)).checkAvailability();
        assert !unavailable.available() && unavailable.reason().contains("Apple Intelligence is not enabled.")
                : "R1.3 — expected unavailable with the command's reason but got: " + unavailable;
    } finally {
        Files.deleteIfExists(log);
    }
}

static String fakeFm(Path log, String... flags) {
    var fake = Path.of("src/test/java/fm/FakeFm.java").toAbsolutePath();
    assert Files.exists(fake) : "fake fm command not found at " + fake;
    return String.join(" ", "java", "--source", "25", "--class-path", classpath(),
            fake.toString(), log.toString(), String.join(" ", flags)).strip();
}

static String classpath() {
    return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(entry -> Path.of(entry).toAbsolutePath().toString())
            .collect(Collectors.joining(File.pathSeparator));
}

static List<String> entries(Path log, String kind) throws IOException {
    if (!Files.exists(log)) {
        return List.of();
    }
    return Files.readAllLines(log).stream().filter(line -> line.startsWith(kind)).toList();
}
