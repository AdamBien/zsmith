import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import airhacks.zsmith.configuration.control.ZCfg;
import airhacks.zsmith.fm.boundary.Fm;
import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.llm.entity.ToolChoice;

/// Traces fm spec R4.1, R4.2, R4.3, R4.4, R4.5, R4.6, R4.7, R4.8, R4.9, R4.10 — see src/main/java/airhacks/zsmith/fm/package-info.java
/// The `fm` command is src/test/java/fm/FakeFm.java; it logs how it was launched and every turn it receives.
/// A `serving <pid> <host> <port>` entry is one server start; the pid tells whether that server still runs.

void main() throws Exception {
    ZCfg.loadBaseConfig("zsmith-test-fm-" + ProcessHandle.current().pid());
    var logs = Files.createTempDirectory("zunit-fm");
    try {
        startsOnTheFirstTurnAndServesEveryTurn(logs.resolve("shared.log"));
        startsAgainOnce(logs.resolve("restart.log"));
        failsWhenNotHealthyInTime(logs.resolve("never-healthy.log"));
        failsWhenTheRestartIsNotHealthy(logs.resolve("healthy-once.log"));
        stopsWithTheProcess(logs.resolve("process-end.log"));
    } finally {
        try (var files = Files.walk(logs)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }
    }
}

void startsOnTheFirstTurnAndServesEveryTurn(Path log) throws Exception {
    var fm = Fm.of(fakeFm(log), Duration.ofSeconds(10));
    try {
        // R4.5 — While no turn has been sent, the BC shall start no server.
        fm.checkAvailability();
        assert entries(log, "serving").isEmpty()
                : "R4.5 — expected no server before the first turn but the log shows: " + Files.readAllLines(log);

        // R4.1 — When the first turn is sent, the BC shall start the server and send the turn once
        // the server reports healthy.
        // R4.2 — While the server is healthy, the BC shall send every turn to it, whichever agent
        // sends it and whether or not turns overlap.
        try (var agents = Executors.newFixedThreadPool(2)) {
            var first = agents.submit(() -> fm.invokeModel(null, user("slow one"), null, 0.5f, ToolChoice.auto));
            var second = agents.submit(() -> fm.invokeModel(null, user("slow two"), null, 0.5f, ToolChoice.auto));
            assert text(first.get(30, TimeUnit.SECONDS)).equals("echo: slow one")
                    && text(second.get(30, TimeUnit.SECONDS)).equals("echo: slow two")
                    : "R4.1 — expected the first turns answered by the started server";
        }
        var third = fm.invokeModel(null, user("three"), null, 0.5f, ToolChoice.auto);
        var all = Files.readAllLines(log);
        assert "echo: three".equals(text(third)) && entries(log, "serving").size() == 1
                : "R4.2 — expected one server for overlapping and later turns but the log shows: " + all;
        assert all.indexOf(entries(log, "serving").getFirst()) < all.indexOf(entries(log, "request").getFirst())
                : "R4.1 — expected the server started before the first turn was sent but the log shows: " + all;

        // R4.3 — The BC shall make the server reachable from this machine only, on a free port it picks itself.
        var serving = entries(log, "serving").getFirst().split(" ");
        assert "127.0.0.1".equals(serving[2]) && Integer.parseInt(serving[3]) > 1024
                : "R4.3 — expected a loopback-only server on a picked port but it was started as: " + String.join(" ", serving);

        // R4.4 — The BC shall send turns only to the server it started.
        assert entries(log, "request").size() == 3
                : "R4.4 — expected all three turns at the server it started but the log shows: " + all;

        // R4.9 — When stopping is requested, the BC shall stop the server it started.
        assert alive(pid(log)) : "R4.9 — expected the server running before the stop";
        fm.stopServer();
        assert !alive(pid(log)) : "R4.9 — expected the server stopped but pid %d is alive".formatted(pid(log));
    } finally {
        fm.stopServer();
    }
}

void startsAgainOnce(Path log) throws Exception {
    var fm = Fm.of(fakeFm(log), Duration.ofSeconds(10));
    try {
        // R4.7 — If the server is no longer running when a turn is sent, then the BC shall start it
        // again once and send the turn.
        fm.invokeModel(null, user("exit"), null, 0.5f, ToolChoice.auto);
        awaitEnd(pid(log));
        var answer = fm.invokeModel(null, user("back"), null, 0.5f, ToolChoice.auto);
        assert "echo: back".equals(text(answer)) && entries(log, "serving").size() == 2
                : "R4.7 — expected one restart and the turn answered but the log shows: " + Files.readAllLines(log);
    } finally {
        fm.stopServer();
    }
}

void failsWhenNotHealthyInTime(Path log) throws Exception {
    var fm = Fm.of(fakeFm(log, "--never-healthy"), Duration.ofSeconds(3));
    try {
        // R4.6 — If the server does not report healthy within the startup limit — 10 seconds unless
        // configured — then the BC shall stop it and fail the turn with what the server printed.
        assert Fm.DEFAULT_STARTUP_LIMIT.equals(Duration.ofSeconds(10))
                : "R4.6 — expected a 10 second startup limit unless configured but got: " + Fm.DEFAULT_STARTUP_LIMIT;
        var failure = failureOf(() -> fm.invokeModel(null, user("hello"), null, 0.5f, ToolChoice.auto));
        assert failure != null && failure.contains("did not report healthy") && failure.contains("cannot load the model assets")
                : "R4.6 — expected the turn failed with what the server printed but got: " + failure;
        assert !alive(pid(log)) : "R4.6 — expected the unhealthy server stopped but pid %d is alive".formatted(pid(log));
    } finally {
        fm.stopServer();
    }
}

void failsWhenTheRestartIsNotHealthy(Path log) throws Exception {
    var fm = Fm.of(fakeFm(log, "--healthy-once"), Duration.ofSeconds(3));
    try {
        // R4.8 — If the server started again does not report healthy either, then the BC shall fail the turn.
        fm.invokeModel(null, user("exit"), null, 0.5f, ToolChoice.auto);
        awaitEnd(pid(log));
        var failure = failureOf(() -> fm.invokeModel(null, user("back"), null, 0.5f, ToolChoice.auto));
        assert failure != null && failure.contains("did not report healthy") && entries(log, "serving").size() == 2
                : "R4.8 — expected the turn failed after one restart but got: %s, log: %s".formatted(failure, Files.readAllLines(log));
    } finally {
        fm.stopServer();
    }
}

void stopsWithTheProcess(Path log) throws Exception {
    // R4.10 — When the process running the agents ends, the BC shall stop the server it started.
    var agents = new ProcessBuilder("java", "--source", "25", "--class-path", classpath(),
            Path.of("src/test/java/fm/TurnThenEnd.java").toAbsolutePath().toString(), fakeFm(log))
            .inheritIO()
            .start();
    assert agents.waitFor(60, TimeUnit.SECONDS) && agents.exitValue() == 0
            : "R4.10 — expected the process running the agents to send its turn and end";
    assert entries(log, "request").size() == 1 : "R4.10 — expected one turn served but the log shows: " + Files.readAllLines(log);
    awaitEnd(pid(log));
    assert !alive(pid(log)) : "R4.10 — expected the server stopped with the process but pid %d is alive".formatted(pid(log));
}

static String text(JSONObject answer) {
    return answer.getJSONArray("content").getJSONObject(0).getString("text");
}

/// The pid of the most recently started server.
static long pid(Path log) throws IOException {
    return Long.parseLong(entries(log, "serving").getLast().split(" ")[1]);
}

static boolean alive(long pid) {
    return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
}

static void awaitEnd(long pid) throws InterruptedException {
    var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (alive(pid) && System.nanoTime() < deadline) {
        Thread.sleep(50);
    }
}

static JSONArray user(String text) {
    return new JSONArray().put(new JSONObject().put("role", "user").put("content", text));
}

static String failureOf(Runnable turn) {
    try {
        turn.run();
        return null;
    } catch (IllegalStateException e) {
        return e.getMessage();
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
