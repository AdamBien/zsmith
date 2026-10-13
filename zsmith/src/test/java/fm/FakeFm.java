import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

/// A stand-in for the `fm` command for the fm BC tests. Not a test itself — launched by
/// the Fm*Test.java files as the configured command.
///
/// Usage: java --source 25 --class-path <zsmith.jar> FakeFm.java <log-file> [flags] available --model <model>
///        java --source 25 --class-path <zsmith.jar> FakeFm.java <log-file> [flags] serve --host <host> --port <port>
///
/// Flags: `--unavailable` reports the model as unavailable, `--never-healthy` serves
/// without ever listening, `--healthy-once` listens on the first start only.
///
/// The reply to a turn is derived from its last message: `call-tool` is answered with a
/// tool call, `fail` with a server error, `slow` after a pause, `exit` normally before the
/// server ends itself; anything else is echoed.
///
/// The log receives `available`, `serving <pid> <host> <port>` and `request <body>` lines.

Path log;

void main(String[] args) throws Exception {
    var arguments = List.of(args);
    this.log = Path.of(args[0]);
    if (arguments.contains("available")) {
        record("available");
        if (arguments.contains("--unavailable")) {
            System.out.println("System model unavailable: Apple Intelligence is not enabled.");
            System.exit(1);
        }
        System.out.println("System model available");
        return;
    }
    var host = arguments.get(arguments.indexOf("--host") + 1);
    var port = Integer.parseInt(arguments.get(arguments.indexOf("--port") + 1));
    var servedBefore = Files.exists(this.log) && Files.readAllLines(this.log).stream().anyMatch(line -> line.startsWith("serving"));
    record("serving %d %s %d".formatted(ProcessHandle.current().pid(), host, port));
    if (arguments.contains("--never-healthy") || (arguments.contains("--healthy-once") && servedBefore)) {
        System.out.println("\u001B[31mcannot load the model assets\u001B[0m");
        Thread.sleep(Long.MAX_VALUE);
    }
    var server = HttpServer.create(new InetSocketAddress(host, port), 0);
    server.createContext("/health", exchange -> respond(exchange, 200, "{\"status\":\"fm serve is running\"}"));
    server.createContext("/v1/chat/completions", this::complete);
    server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    server.start();
    System.out.println("listening on http://%s:%d".formatted(host, port));
}

void complete(HttpExchange exchange) throws IOException {
    var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    record("request " + body);
    var request = new JSONObject(body);
    var messages = request.getJSONArray("messages");
    var last = messages.getJSONObject(messages.length() - 1).optString("content", "");
    if (last.contains("fail")) {
        respond(exchange, 500, "{\"error\":{\"code\":\"500\",\"message\":\"An unsupported generation guide was used.\",\"type\":\"server_error\"}}");
        return;
    }
    if (last.contains("slow")) {
        pause(500);
    }
    var message = new JSONObject().put("role", "assistant");
    var finishReason = "stop";
    if (last.contains("call-tool")) {
        finishReason = "tool_calls";
        message.put("content", JSONObject.NULL).put("tool_calls", new JSONArray().put(new JSONObject()
                .put("id", "call_7")
                .put("type", "function")
                .put("function", new JSONObject().put("name", "get_weather").put("arguments", "{\"city\":\"Munich\"}"))));
    } else {
        message.put("content", "echo: " + last);
    }
    var completion = new JSONObject()
            .put("model", request.getString("model"))
            .put("choices", new JSONArray().put(new JSONObject().put("index", 0).put("message", message).put("finish_reason", finishReason)))
            .put("usage", new JSONObject().put("prompt_tokens", 65).put("completion_tokens", 3).put("total_tokens", 68));
    respond(exchange, 200, completion.toString());
    if (last.contains("exit")) {
        System.exit(0);
    }
}

void respond(HttpExchange exchange, int status, String body) throws IOException {
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var out = exchange.getResponseBody()) {
        out.write(bytes);
    }
}

void pause(long millis) {
    try {
        Thread.sleep(millis);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}

synchronized void record(String entry) throws IOException {
    Files.writeString(this.log, entry + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
}
