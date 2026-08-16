package airhacks.zsmith.tools.control;

import airhacks.zsmith.tools.boundary.Tool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.logging.control.Log;

public interface ExecuteScriptTool {

    int DEFAULT_TIMEOUT_SECONDS = 30;

    enum Field { path }

    static Tool create() {
        return create(DEFAULT_TIMEOUT_SECONDS);
    }

    static Tool create(int timeoutSeconds) {
        return Tool.of(
                "execute_script",
                "Executes a script and returns its output",
                Tool.schema(Tool.Prop.string(Field.path, "Path to the script to execute")),
                input -> run(input, timeoutSeconds));
    }

    private static String run(JSONObject input, int timeoutSeconds) {
        var scriptPath = Path.of(input.getString(Field.path.name()));
        if (!Files.isRegularFile(scriptPath)) {
            return "Error: Script not found: " + scriptPath;
        }
        if (!Files.isExecutable(scriptPath)) {
            return "Error: Script not executable: " + scriptPath;
        }
        try {
            Log.tool("executing script: " + scriptPath);
            var process = new ProcessBuilder(scriptPath.toAbsolutePath().toString())
                    .directory(Path.of(System.getProperty("user.dir")).toFile())
                    .redirectErrorStream(true)
                    .start();

            var output = new String(process.getInputStream().readAllBytes());
            var completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                return output + "\nError: Script timed out after %ds".formatted(timeoutSeconds);
            }

            var exitCode = process.exitValue();
            if (exitCode != 0) {
                return output + "\nError: Script exited with code %d".formatted(exitCode);
            }

            return output.strip();
        } catch (IOException e) {
            return "Error: Script execution failed: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: Script interrupted";
        }
    }
}
