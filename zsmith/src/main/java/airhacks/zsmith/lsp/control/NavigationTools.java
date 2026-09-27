package airhacks.zsmith.lsp.control;

import java.util.List;

import airhacks.zsmith.json.JSONObject;
import airhacks.zsmith.lsp.boundary.Lsp;
import airhacks.zsmith.tools.boundary.Tool;

/// The navigation operations as handlers fulfilling the tools BC's published
/// contract, so an agent equips them exactly like file access.
public interface NavigationTools {

    enum Field { query, path, line, column }

    String LOCATION_FORMAT = " Returns one location per line as <path>:<line>:<column>: <source line>, "
            + "paths relative to the workspace root, lines and columns one-based.";

    @FunctionalInterface
    interface Positional {
        String at(String path, int line, int column);
    }

    static List<Tool> of(Lsp lsp) {
        return List.of(
                Tool.of("find_symbols",
                        "Finds symbols by name across the whole workspace through the language server."
                                + LOCATION_FORMAT,
                        Tool.schema(Tool.Prop.string(Field.query, "Symbol name, or a part of it")),
                        input -> lsp.findSymbols(input.optString(Field.query.name(), ""))),
                positional("find_definition",
                        "Finds where the symbol at a position is defined.", lsp::findDefinition),
                positional("find_references",
                        "Finds every use of the symbol at a position, including its declaration.", lsp::findReferences),
                positional("find_implementations",
                        "Finds every implementation of the interface or abstract member at a position.",
                        lsp::findImplementations),
                positional("find_callers",
                        "Finds every call into the method at a position.", lsp::findCallers),
                positional("find_callees",
                        "Finds every call the method at a position makes.", lsp::findCallees));
    }

    private static Tool positional(String name, String description, Positional navigation) {
        return Tool.of(
                name,
                description + LOCATION_FORMAT,
                Tool.schema(
                        Tool.Prop.string(Field.path, "File path relative to the workspace root"),
                        Tool.Prop.integer(Field.line, "One-based line of the symbol"),
                        Tool.Prop.integer(Field.column, "One-based column of the symbol")),
                input -> run(input, navigation));
    }

    private static String run(JSONObject input, Positional navigation) {
        if (!input.has(Field.path.name())) {
            return "Error: Missing required parameter: path";
        }
        return navigation.at(
                input.getString(Field.path.name()),
                input.optInt(Field.line.name(), 0),
                input.optInt(Field.column.name(), 0));
    }
}
