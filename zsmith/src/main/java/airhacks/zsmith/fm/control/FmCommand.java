package airhacks.zsmith.fm.control;

import static airhacks.zsmith.Concern.Kind.EXTERNAL_SYSTEM;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import airhacks.zsmith.Concern;
import airhacks.zsmith.fm.entity.Availability;

/// The `fm` command as it is launched: the executable and any leading arguments. Only two
/// of its subcommands are used — `available` to probe and `serve` to run the local server.
@Concern(EXTERNAL_SYSTEM)
public record FmCommand(List<String> launch) {

    static final Duration PROBE_LIMIT = Duration.ofSeconds(10);

    public static FmCommand of(String launchCommand) {
        return new FmCommand(Arrays.stream(launchCommand.trim().split("\\s+")).toList());
    }

    /// A command that cannot be launched at all is the missing-command case; one that runs
    /// and says no carries its own reason.
    public Availability checkAvailability(String model) {
        Process probe;
        try {
            probe = new ProcessBuilder(with("available", "--model", model))
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            return Availability.ofUnavailable(
                    "the `%s` command is not installed — the on-device model is reached through it"
                            .formatted(executable()));
        }
        try {
            var printed = plain(new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            if (!probe.waitFor(PROBE_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
                probe.destroyForcibly();
                return Availability.ofUnavailable("`%s available` did not answer within %ds"
                        .formatted(executable(), PROBE_LIMIT.toSeconds()));
            }
            var available = probe.exitValue() == 0 && !printed.contains("unavailable");
            return available ? Availability.ofAvailable() : Availability.ofUnavailable(printed);
        } catch (IOException e) {
            return Availability.ofUnavailable("`%s available` could not be read: %s".formatted(executable(), e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            probe.destroyForcibly();
            return Availability.ofUnavailable("interrupted while checking `%s available`".formatted(executable()));
        }
    }

    /// Everything the server prints goes to a file rather than a pipe: it logs every
    /// request, and a pipe nobody drains would eventually block it.
    public Process serve(String host, int port, Path printed) throws IOException {
        return new ProcessBuilder(with("serve", "--host", host, "--port", String.valueOf(port)))
                .redirectErrorStream(true)
                .redirectOutput(printed.toFile())
                .start();
    }

    public String executable() {
        return this.launch.getFirst();
    }

    List<String> with(String... arguments) {
        var command = new ArrayList<>(this.launch);
        command.addAll(List.of(arguments));
        return command;
    }

    /// The command colours its output even when it is not a terminal.
    static String plain(String printed) {
        return printed.replaceAll("\u001B\\[[0-9;]*m", "").strip();
    }
}
