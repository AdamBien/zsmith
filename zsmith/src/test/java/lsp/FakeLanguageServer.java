import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;

/// A minimal language server over standard streams for the lsp BC tests. Not a
/// test itself — launched by the *Test.java files as the configured server.
///
/// Usage: java --source 25 --class-path <zsmith.jar> FakeLanguageServer.java <log-file> [--without-implementation]
///
/// It derives every answer from the workspace files instead of a canned script:
/// the symbol at a position is the identifier under the cursor, declarations are
/// lines with `class`, `interface` or `void` before the name, implementations are
/// declarations in files that say `implements`, and calls are `.name(` sites.
/// Special queries: `crash` exits without answering, `hang` never answers.
/// The symbol `String` resolves to a library location outside the workspace.
///
/// Every received method is appended to the log file, as is `response:<id>` for
/// each answer the client gives to a server-initiated request.

static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

Path log;
Path root;
boolean withImplementation = true;
final BufferedInputStream in = new BufferedInputStream(System.in);

void main(String[] args) throws IOException {
    this.log = Path.of(args[0]);
    this.withImplementation = !List.of(args).contains("--without-implementation");
    record("started");
    while (true) {
        var message = read();
        if (message == null) {
            System.exit(0);
        }
        handle(message);
    }
}

void handle(JSONObject message) throws IOException {
    var method = message.optString("method", null);
    if (method == null) {
        record("response:" + message.get("id"));
        return;
    }
    record(method);
    var params = message.optJSONObject("params", new JSONObject());
    switch (method) {
        case "initialize" -> {
            this.root = Path.of(URI.create(params.getString("rootUri")));
            send(new JSONObject().put("jsonrpc", "2.0").put("id", "srv-1")
                    .put("method", "client/registerCapability")
                    .put("params", new JSONObject().put("registrations", new JSONArray())));
            send(new JSONObject().put("jsonrpc", "2.0").put("method", "window/logMessage")
                    .put("params", new JSONObject().put("type", 3).put("message", "fake server ready")));
            respond(message, new JSONObject().put("capabilities", capabilities()));
        }
        case "initialized", "textDocument/didOpen", "textDocument/didClose" -> { }
        case "shutdown" -> respond(message, JSONObject.NULL);
        case "exit" -> System.exit(0);
        case "workspace/symbol" -> symbols(message, params.getString("query"));
        case "textDocument/definition" -> {
            var word = wordAt(params);
            respond(message, locations(word, declarations(word)));
        }
        case "textDocument/references" -> {
            var word = wordAt(params);
            respond(message, locations(word, occurrences(word)));
        }
        case "textDocument/implementation" -> {
            var word = wordAt(params);
            respond(message, locations(word, implementations(word)));
        }
        case "textDocument/prepareCallHierarchy" -> prepareCallHierarchy(message, params);
        case "callHierarchy/incomingCalls" -> incomingCalls(message, params.getJSONObject("item"));
        case "callHierarchy/outgoingCalls" -> outgoingCalls(message, params.getJSONObject("item"));
        default -> send(new JSONObject().put("jsonrpc", "2.0").put("id", message.get("id"))
                .put("error", new JSONObject().put("code", -32601).put("message", "method not found: " + method)));
    }
}

JSONObject capabilities() {
    return new JSONObject()
            .put("workspaceSymbolProvider", true)
            .put("definitionProvider", true)
            .put("referencesProvider", true)
            .put("implementationProvider", this.withImplementation)
            .put("callHierarchyProvider", true);
}

void symbols(JSONObject message, String query) throws IOException {
    if ("crash".equals(query)) {
        System.exit(3);
    }
    if ("hang".equals(query)) {
        return;
    }
    var found = new JSONArray();
    for (var hit : declarations(query)) {
        found.put(new JSONObject().put("name", query).put("kind", 5).put("location", location(hit)));
    }
    respond(message, found);
}

void prepareCallHierarchy(JSONObject message, JSONObject params) throws IOException {
    var word = wordAt(params);
    var methods = declarations(word).stream().filter(hit -> hit.text().contains("void " + word + "(")).toList();
    if (methods.isEmpty()) {
        respond(message, JSONObject.NULL);
        return;
    }
    respond(message, new JSONArray().put(item(word, methods.getFirst())));
}

void incomingCalls(JSONObject message, JSONObject item) throws IOException {
    var name = item.getString("name");
    var calls = new JSONArray();
    for (var site : matches(Pattern.compile("(?<=\\.)" + Pattern.quote(name) + "\\("), null)) {
        calls.put(new JSONObject()
                .put("from", item("caller", site))
                .put("fromRanges", new JSONArray().put(range(site))));
    }
    respond(message, calls);
}

void outgoingCalls(JSONObject message, JSONObject item) throws IOException {
    var file = Path.of(URI.create(item.getString("uri")));
    var fromLine = item.getJSONObject("range").getJSONObject("start").getInt("line");
    var calls = new JSONArray();
    for (var site : matches(Pattern.compile("(?<=\\.)[A-Za-z_]\\w*(?=\\()"), file)) {
        if (site.line() <= fromLine) {
            continue;
        }
        calls.put(new JSONObject()
                .put("to", item(site.text().substring(site.character()).split("\\(")[0], site))
                .put("fromRanges", new JSONArray().put(range(site))));
    }
    respond(message, calls);
}

record Hit(Path file, int line, int character, int length, String text) { }

List<Hit> occurrences(String word) {
    if (word.isEmpty()) {
        return List.of();
    }
    return matches(Pattern.compile("\\b" + Pattern.quote(word) + "\\b"), null);
}

List<Hit> declarations(String word) {
    return occurrences(word).stream()
            .filter(hit -> hit.text().matches(".*\\b(class|interface|void)\\s+" + Pattern.quote(word) + "\\b.*"))
            .toList();
}

List<Hit> implementations(String word) {
    return declarations(word).stream()
            .filter(hit -> contentOf(hit.file()).contains("implements"))
            .toList();
}

List<Hit> matches(Pattern pattern, Path onlyFile) {
    var hits = new ArrayList<Hit>();
    for (var file : onlyFile == null ? sourceFiles() : List.of(onlyFile)) {
        var lines = contentOf(file).split("\n", -1);
        for (var i = 0; i < lines.length; i++) {
            var matcher = pattern.matcher(lines[i]);
            while (matcher.find()) {
                hits.add(new Hit(file, i, matcher.start(), matcher.end() - matcher.start(), lines[i]));
            }
        }
    }
    return hits;
}

List<Path> sourceFiles() {
    try (Stream<Path> walk = Files.walk(this.root)) {
        return walk.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java")).sorted().toList();
    } catch (IOException e) {
        return List.of();
    }
}

String contentOf(Path file) {
    try {
        return Files.readString(file);
    } catch (IOException e) {
        return "";
    }
}

String wordAt(JSONObject params) {
    var file = Path.of(URI.create(params.getJSONObject("textDocument").getString("uri")));
    var position = params.getJSONObject("position");
    var lines = contentOf(file).split("\n", -1);
    var line = position.getInt("line");
    var character = position.getInt("character");
    if (line >= lines.length) {
        return "";
    }
    var matcher = IDENTIFIER.matcher(lines[line]);
    while (matcher.find()) {
        if (matcher.start() <= character && character < matcher.end()) {
            return matcher.group();
        }
    }
    return "";
}

/// `String` is the one symbol the fake places outside the workspace, the way a
/// real server points into a library.
JSONArray locations(String word, List<Hit> hits) {
    if ("String".equals(word)) {
        return new JSONArray().put(new JSONObject()
                .put("uri", "jdt://contents/java.base/java.lang/String.class")
                .put("range", zeroRange()));
    }
    var found = new JSONArray();
    for (var hit : hits) {
        found.put(location(hit));
    }
    return found;
}

JSONObject location(Hit hit) {
    return new JSONObject().put("uri", hit.file().toUri().toString()).put("range", range(hit));
}

JSONObject item(String name, Hit hit) {
    return new JSONObject()
            .put("name", name)
            .put("kind", 6)
            .put("uri", hit.file().toUri().toString())
            .put("range", range(hit))
            .put("selectionRange", range(hit));
}

JSONObject range(Hit hit) {
    return new JSONObject()
            .put("start", new JSONObject().put("line", hit.line()).put("character", hit.character()))
            .put("end", new JSONObject().put("line", hit.line()).put("character", hit.character() + hit.length()));
}

JSONObject zeroRange() {
    return new JSONObject()
            .put("start", new JSONObject().put("line", 0).put("character", 0))
            .put("end", new JSONObject().put("line", 0).put("character", 0));
}

void respond(JSONObject request, Object result) throws IOException {
    send(new JSONObject().put("jsonrpc", "2.0").put("id", request.get("id")).put("result", result));
}

void send(JSONObject message) throws IOException {
    var body = message.toString().getBytes(StandardCharsets.UTF_8);
    System.out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    System.out.write(body);
    System.out.flush();
}

JSONObject read() throws IOException {
    var length = -1;
    String header;
    while ((header = headerLine()) != null) {
        if (header.isEmpty()) {
            if (length >= 0) {
                break;
            }
            continue;
        }
        if (header.toLowerCase().startsWith("content-length:")) {
            length = Integer.parseInt(header.substring("content-length:".length()).trim());
        }
    }
    if (header == null || length < 0) {
        return null;
    }
    var body = this.in.readNBytes(length);
    return body.length < length ? null : new JSONObject(new String(body, StandardCharsets.UTF_8));
}

String headerLine() throws IOException {
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

void record(String entry) throws IOException {
    Files.writeString(this.log, entry + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
}
