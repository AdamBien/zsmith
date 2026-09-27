package airhacks.zsmith.lsp.control;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import airhacks.zsmith.json.JSONArray;
import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.lsp.entity.Location;

/// Turns what the server answers — `Location`, `Location[]`,
/// `LocationLink[]`, `SymbolInformation[]`, call hierarchy entries — into
/// workspace-relative [Location]s, and those into the one report format
/// every navigation tool returns.
public record Locations(Path root) {

    static final int MAX_LOCATIONS = 200;
    static final String NONE_FOUND = "No locations found";

    public String report(List<Location> found) {
        if (found.isEmpty()) {
            return NONE_FOUND;
        }
        var ordered = found.stream().distinct().sorted().toList();
        if (ordered.size() > MAX_LOCATIONS) {
            return join(ordered.subList(0, MAX_LOCATIONS))
                    + "\n... truncated at " + MAX_LOCATIONS + " locations";
        }
        return join(ordered);
    }

    static String join(List<Location> locations) {
        return locations.stream().map(Location::format).collect(Collectors.joining("\n"));
    }

    /// `Location | Location[] | LocationLink[] | null`, the shapes definition,
    /// references and implementation answers take.
    public List<Location> fromResult(Object result) {
        return switch (result) {
            case JSONObject single -> fromLocation(single).map(List::of).orElse(List.of());
            case JSONArray many -> objects(many).stream()
                    .flatMap(location -> fromLocation(location).stream())
                    .toList();
            default -> List.of();
        };
    }

    /// `SymbolInformation[] | WorkspaceSymbol[]`. A workspace symbol may carry
    /// only a URI and leave the range to a resolve round-trip.
    public List<Location> fromSymbols(Object result, LanguageServer server) {
        if (!(result instanceof JSONArray symbols)) {
            return List.of();
        }
        var found = new ArrayList<Location>();
        for (var symbol : objects(symbols)) {
            var location = symbol.optJSONObject("location");
            if (location == null) {
                continue;
            }
            if (!location.has("range") && server.capabilities().resolvesWorkspaceSymbols()) {
                var resolved = server.request("workspaceSymbol/resolve", symbol);
                location = resolved instanceof JSONObject full ? full.optJSONObject("location", location) : location;
            }
            fromLocation(location).ifPresent(found::add);
        }
        return found;
    }

    /// Call hierarchy entries: each carries the ranges of its call sites, and
    /// the caller decides which document those ranges belong to — the calling
    /// item's for incoming calls, the queried item's for outgoing ones.
    public List<Location> fromCalls(Object result, Function<JSONObject, String> documentOf) {
        if (!(result instanceof JSONArray calls)) {
            return List.of();
        }
        var found = new ArrayList<Location>();
        for (var call : objects(calls)) {
            var uri = documentOf.apply(call);
            for (var range : objects(call.optJSONArray("fromRanges", new JSONArray()))) {
                found.add(at(uri, range.getJSONObject("start")));
            }
        }
        return found;
    }

    Optional<Location> fromLocation(JSONObject location) {
        var uri = location.optString("uri", location.optString("targetUri", null));
        if (uri == null) {
            return Optional.empty();
        }
        var range = location.optJSONObject("range",
                location.optJSONObject("targetSelectionRange",
                        location.optJSONObject("targetRange", new JSONObject())));
        var start = range.optJSONObject("start", new JSONObject());
        return Optional.of(at(uri, start));
    }

    Location at(String uri, JSONObject start) {
        var zeroBasedLine = start.optInt("line", 0);
        var line = zeroBasedLine + 1;
        var column = start.optInt("character", 0) + 1;
        return workspaceFile(uri)
                .map(file -> Location.inWorkspace(
                        this.root.relativize(file).toString(), line, column, lineText(file, zeroBasedLine)))
                .orElseGet(() -> Location.outside(uri, line, column));
    }

    Optional<Path> workspaceFile(String uri) {
        try {
            var parsed = URI.create(uri);
            if (!"file".equalsIgnoreCase(parsed.getScheme())) {
                return Optional.empty();
            }
            var file = Path.of(parsed).normalize();
            return file.startsWith(this.root) ? Optional.of(file) : Optional.empty();
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }

    static String lineText(Path file, int zeroBasedLine) {
        try {
            var lines = Files.readAllLines(file);
            return zeroBasedLine < lines.size() ? lines.get(zeroBasedLine) : "";
        } catch (IOException _) {
            return "";
        }
    }

    static List<JSONObject> objects(JSONArray array) {
        var objects = new ArrayList<JSONObject>();
        for (var element : array) {
            if (element instanceof JSONObject object) {
                objects.add(object);
            }
        }
        return objects;
    }
}
