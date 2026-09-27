package airhacks.zsmith.lsp.control;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.logging.control.Log;

/// JSON-RPC 2.0 over a pair of byte streams, framed the way the Language
/// Server Protocol prescribes: a `Content-Length` header block, a blank line,
/// then the UTF-8 body. A reader thread correlates responses with the
/// requests waiting for them, answers the requests the server initiates and
/// lets its notifications pass, so the server never blocks on the client.
public class JsonRpcConnection implements AutoCloseable {

    static final String JSONRPC_VERSION = "2.0";
    static final String CONTENT_LENGTH = "Content-Length:";

    final InputStream in;
    final OutputStream out;
    final Duration timeout;
    final AtomicInteger ids = new AtomicInteger();
    final Map<Integer, CompletableFuture<JSONObject>> pending = new ConcurrentHashMap<>();
    volatile boolean closed;

    public JsonRpcConnection(InputStream in, OutputStream out, Duration timeout) {
        this.in = new BufferedInputStream(in);
        this.out = out;
        this.timeout = timeout;
        Thread.ofPlatform().daemon().name("lsp-reader").start(this::readLoop);
    }

    /// Sends a request and blocks for its result — the `result` member, which
    /// may be `JSONObject.NULL`. A protocol error, a closed connection or a
    /// silent server surface as [LanguageServerFailure].
    public Object request(String method, JSONObject params) {
        var id = this.ids.incrementAndGet();
        var response = new CompletableFuture<JSONObject>();
        this.pending.put(id, response);
        send(new JSONObject()
                .put("jsonrpc", JSONRPC_VERSION)
                .put("id", id)
                .put("method", method)
                .put("params", params));
        try {
            var message = response.get(this.timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (message.has("error")) {
                var error = message.getJSONObject("error");
                throw new LanguageServerFailure(method + " failed: " + error.optString("message", error.toString()));
            }
            return message.opt("result");
        } catch (TimeoutException e) {
            this.pending.remove(id);
            throw LanguageServerFailure.timeout(method, this.timeout);
        } catch (ExecutionException e) {
            throw new LanguageServerFailure(e.getCause().getMessage(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LanguageServerFailure("interrupted while waiting for " + method, e);
        }
    }

    public void notify(String method, JSONObject params) {
        send(new JSONObject()
                .put("jsonrpc", JSONRPC_VERSION)
                .put("method", method)
                .put("params", params));
    }

    synchronized void send(JSONObject message) {
        var body = message.toString().getBytes(StandardCharsets.UTF_8);
        try {
            this.out.write((CONTENT_LENGTH + " " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            this.out.write(body);
            this.out.flush();
        } catch (IOException e) {
            throw new LanguageServerFailure("cannot write to the language server: " + e.getMessage(), e);
        }
    }

    void readLoop() {
        try {
            while (!this.closed) {
                var message = readMessage();
                if (message == null) {
                    break;
                }
                dispatch(message);
            }
        } catch (IOException e) {
            Log.debug("lsp connection read failed: " + e.getMessage());
        } finally {
            failPending();
        }
    }

    void dispatch(JSONObject message) {
        var isRequestOrNotification = message.has("method");
        var carriesId = message.has("id") && !message.isNull("id");
        if (isRequestOrNotification && carriesId) {
            answerServerRequest(message);
        } else if (isRequestOrNotification) {
            Log.debug("lsp notification: " + message.getString("method"));
        } else if (carriesId) {
            var waiting = this.pending.remove(message.optInt("id", -1));
            if (waiting != null) {
                waiting.complete(message);
            }
        }
    }

    /// Every server-initiated request gets an empty answer: `null`, or for
    /// `workspace/configuration` one `null` per requested item, which is the
    /// shape that request demands. That is enough for capability
    /// registration, progress tokens and configuration lookups to proceed.
    void answerServerRequest(JSONObject request) {
        var method = request.getString("method");
        Log.debug("lsp server request: " + method);
        var result = "workspace/configuration".equals(method)
                ? nullPerItem(request.optJSONObject("params", new JSONObject()))
                : JSONObject.NULL;
        try {
            send(new JSONObject()
                    .put("jsonrpc", JSONRPC_VERSION)
                    .put("id", request.get("id"))
                    .put("result", result));
        } catch (LanguageServerFailure e) {
            Log.debug("could not answer " + method + ": " + e.getMessage());
        }
    }

    static JSONArray nullPerItem(JSONObject params) {
        var answers = new JSONArray();
        var items = params.optJSONArray("items", new JSONArray());
        for (var i = 0; i < items.length(); i++) {
            answers.put(JSONObject.NULL);
        }
        return answers;
    }

    void failPending() {
        this.closed = true;
        this.pending.values().forEach(waiting -> waiting.completeExceptionally(LanguageServerFailure.connectionClosed()));
        this.pending.clear();
    }

    /// The next framed message, or `null` once the stream ends.
    JSONObject readMessage() throws IOException {
        var length = -1;
        String header;
        while ((header = readHeaderLine()) != null) {
            if (header.isEmpty()) {
                if (length >= 0) {
                    break;
                }
                continue;
            }
            if (header.regionMatches(true, 0, CONTENT_LENGTH, 0, CONTENT_LENGTH.length())) {
                length = Integer.parseInt(header.substring(CONTENT_LENGTH.length()).trim());
            }
        }
        if (header == null || length < 0) {
            return null;
        }
        var body = this.in.readNBytes(length);
        if (body.length < length) {
            return null;
        }
        return new JSONObject(new String(body, StandardCharsets.UTF_8));
    }

    String readHeaderLine() throws IOException {
        var line = new ByteArrayOutputStream();
        int next;
        while ((next = this.in.read()) != -1) {
            if (next == '\n') {
                var text = line.toString(StandardCharsets.US_ASCII);
                return text.endsWith("\r") ? text.substring(0, text.length() - 1) : text;
            }
            line.write(next);
        }
        return null;
    }

    @Override
    public void close() {
        failPending();
        try {
            this.out.close();
        } catch (IOException _) {
            // the process is going away with its streams
        }
    }
}
