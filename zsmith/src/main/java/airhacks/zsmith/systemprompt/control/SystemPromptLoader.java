package airhacks.zsmith.systemprompt.control;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Loads a system prompt from system.prompt files in order:
/// 1. ~/.{appName}/{agentName}/system.prompt (global agent-specific)
/// 2. ./{agentName}/system.prompt (local agent-specific)
/// 3. ./system.prompt (highest priority)
///
/// Each layer overwrites the previous.
public class SystemPromptLoader {

    static final String SYSTEM_PROMPT_FILE = "system.prompt";

    /// A persisted prompt wins over anything passed in code, because prompts are engineered
    /// and versioned outside the code. Without one, the first non-null fallback is used, in
    /// the order given — typically the prompt named at construction, then the configured default.
    public static String resolve(String appName, String agentName, String... fallbacks) {
        var persisted = load(appName, agentName);
        if (persisted != null) {
            return persisted;
        }
        for (var fallback : fallbacks) {
            if (fallback != null) {
                return fallback;
            }
        }
        return null;
    }

    public static String load(String appName, String agentName) {
        var userHome = System.getProperty("user.home");
        String prompt = null;
        var globalPrompt = Path.of(userHome, "." + appName, agentName, SYSTEM_PROMPT_FILE);
        if (Files.exists(globalPrompt)) {
            prompt = readTextFile(globalPrompt);
        }
        var localPrompt = Path.of(agentName, SYSTEM_PROMPT_FILE);
        if (Files.exists(localPrompt)) {
            prompt = readTextFile(localPrompt);
        }
        var basePrompt = Path.of(SYSTEM_PROMPT_FILE);
        if (Files.exists(basePrompt)) {
            prompt = readTextFile(basePrompt);
        }
        return prompt;
    }

    static String readTextFile(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read file: " + file, e);
        }
    }
}
