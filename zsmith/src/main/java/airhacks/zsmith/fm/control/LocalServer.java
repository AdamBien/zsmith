package airhacks.zsmith.fm.control;

import static airhacks.zsmith.Concern.Kind.EXTERNAL_SYSTEM;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import airhacks.zsmith.Concern;
import airhacks.zsmith.configuration.control.HttpTimeouts;
import airhacks.zsmith.logging.control.Log;

/// The one `fm serve` process this component runs: started on the first turn, reused by
/// every later one, started again when it is found dead, stopped on request or with the JVM.
///
/// A class because it owns the process — the only mutable state of the component.
@Concern(EXTERNAL_SYSTEM)
public class LocalServer {

    static final String HOST = "127.0.0.1";
    static final Duration HEALTH_POLL = Duration.ofMillis(100);
    static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(1);
    static final Duration STOP_LIMIT = Duration.ofSeconds(3);

    final FmCommand command;
    final Duration startupLimit;
    Process process;
    int port;
    Path printed;
    boolean stopsWithJvm;

    public LocalServer(FmCommand command, Duration startupLimit) {
        this.command = command;
        this.startupLimit = startupLimit;
    }

    /// Where a turn is sent. Synchronized so overlapping first turns start one server, not two.
    public synchronized URI chatCompletions(String model) {
        if (running()) {
            return endpoint("/v1/chat/completions");
        }
        if (this.process != null) {
            Log.warning("fm server is no longer running, starting it again");
            stop();
        }
        var availability = this.command.checkAvailability(model);
        if (!availability.available()) {
            throw new IllegalStateException("the on-device model is unavailable: " + availability.reason());
        }
        start();
        return endpoint("/v1/chat/completions");
    }

    void start() {
        try {
            this.port = freePort();
            this.printed = Files.createTempFile("zsmith-fm-serve", ".log");
            Log.agent("starting fm server on %s:%d".formatted(HOST, this.port));
            this.process = this.command.serve(HOST, this.port, this.printed);
        } catch (IOException e) {
            throw new IllegalStateException("the fm server could not be started: " + e.getMessage(), e);
        }
        stopWithJvm();
        if (!awaitHealthy()) {
            var output = printed();
            stop();
            throw new IllegalStateException("the fm server did not report healthy within %s: %s"
                    .formatted(this.startupLimit, output));
        }
    }

    boolean running() {
        return this.process != null && this.process.isAlive();
    }

    boolean awaitHealthy() {
        var deadline = System.nanoTime() + this.startupLimit.toNanos();
        while (System.nanoTime() < deadline && this.process.isAlive()) {
            if (healthy()) {
                return true;
            }
            try {
                Thread.sleep(HEALTH_POLL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    boolean healthy() {
        var request = HttpRequest.newBuilder(endpoint("/health")).timeout(HEALTH_TIMEOUT).GET().build();
        try {
            return HttpTimeouts.client().send(request, BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public synchronized void stop() {
        if (this.process == null) {
            return;
        }
        this.process.destroy();
        try {
            if (!this.process.waitFor(STOP_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
                this.process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.process.destroyForcibly();
        }
        this.process = null;
        try {
            Files.deleteIfExists(this.printed);
        } catch (IOException e) {
            Log.warning("cannot delete fm server output %s: %s".formatted(this.printed, e.getMessage()));
        }
    }

    void stopWithJvm() {
        if (this.stopsWithJvm) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "fm-shutdown"));
        this.stopsWithJvm = true;
    }

    String printed() {
        try {
            return FmCommand.plain(Files.readString(this.printed));
        } catch (IOException e) {
            return "(server output unreadable: %s)".formatted(e.getMessage());
        }
    }

    URI endpoint(String path) {
        return URI.create("http://%s:%d%s".formatted(HOST, this.port, path));
    }

    /// Picked by binding and releasing: the command takes a port number, not a bound socket.
    static int freePort() throws IOException {
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName(HOST))) {
            return socket.getLocalPort();
        }
    }
}
