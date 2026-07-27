package airhacks.zsmith.tools.boundary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import airhacks.zsmith.logging.control.Log;

public class SandboxedFileSystem {

    static final int MAX_SEARCH_MATCHES = 200;

    Path rootDirectory;

    public SandboxedFileSystem(Path rootDirectory) {
        this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
        Log.info("Sandbox root: " + this.rootDirectory);
    }

    Path resolve(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("Invalid path");
        }
        if (relativePath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid path");
        }
        if (relativePath.startsWith("/") || (relativePath.length() >= 2 && relativePath.charAt(1) == ':')) {
            throw new IllegalArgumentException("Invalid path");
        }
        if (containsDotDot(relativePath)) {
            throw new IllegalArgumentException("Invalid path");
        }
        var resolved = this.rootDirectory.resolve(relativePath).normalize();
        if (!resolved.startsWith(this.rootDirectory)) {
            throw new IllegalArgumentException("Invalid path");
        }
        if (Files.exists(resolved) && Files.isSymbolicLink(resolved)) {
            try {
                var target = resolved.toRealPath();
                if (!target.startsWith(this.rootDirectory)) {
                    throw new IllegalArgumentException("Invalid path");
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid path");
            }
        }
        return resolved;
    }

    public String readFile(String relativePath) {
        Log.debug("Reading file: " + relativePath);
        Path resolved;
        try {
            resolved = resolve(relativePath);
        } catch (IllegalArgumentException e) {
            Log.error("Invalid path: " + relativePath);
            throw e;
        }
        try {
            var content = Files.readString(resolved);
            Log.debug("Read " + content.length() + " chars from " + relativePath);
            return content;
        } catch (java.nio.file.NoSuchFileException e) {
            Log.warning("File not found: " + relativePath);
            return "Error: File not found";
        } catch (IOException e) {
            Log.error("Could not read file: " + relativePath, e);
            return "Error: Could not read file";
        }
    }

    public void writeFile(String relativePath, String content) {
        writeFile(relativePath, content, false);
    }

    public void writeFile(String relativePath, String content, boolean append) {
        Log.debug((append ? "Appending to file: " : "Writing file: ") + relativePath);
        Path resolved;
        try {
            resolved = resolve(relativePath);
        } catch (IllegalArgumentException e) {
            Log.error("Invalid path: " + relativePath);
            throw e;
        }
        try {
            var parent = resolved.getParent();
            if (parent != null && !Files.exists(parent)) {
                Log.debug("Creating directories: " + parent);
                Files.createDirectories(parent);
            }
            if (append) {
                Files.writeString(resolved, content,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } else {
                Files.writeString(resolved, content);
            }
            Log.debug((append ? "Appended " : "Wrote ") + content.length() + " chars to " + relativePath);
        } catch (IOException e) {
            Log.error("Could not write file: " + relativePath, e);
            throw new RuntimeException("Error: Could not write file: " + e.getMessage(), e);
        }
    }

    public String listFiles() {
        Log.debug("Listing files in sandbox");
        try (var stream = Files.walk(this.rootDirectory)) {
            var files = stream
                    .filter(Files::isRegularFile)
                    .map(this.rootDirectory::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
            if (files.isEmpty()) {
                Log.debug("No files found in sandbox");
                return "No files found in sandbox";
            }
            Log.debug("Found " + files.size() + " files");
            return String.join("\n", files);
        } catch (IOException e) {
            Log.error("Could not list files", e);
            return "Error: Could not list files";
        }
    }

    public String listFiles(String ending) {
        Log.debug("Listing files ending with: " + ending);
        try (var stream = Files.walk(this.rootDirectory)) {
            var files = stream
                    .filter(Files::isRegularFile)
                    .filter(file -> fileNameEndsWith(file, ending))
                    .map(this.rootDirectory::relativize)
                    .map(Path::toString)
                    .sorted()
                    .toList();
            if (files.isEmpty()) {
                Log.debug("No files ending with " + ending + " found");
                return "No files ending with " + ending + " found";
            }
            Log.debug("Found " + files.size() + " files ending with " + ending);
            return String.join("\n", files);
        } catch (IOException e) {
            Log.error("Could not list files", e);
            return "Error: Could not list files";
        }
    }

    public String searchFiles(String pattern, String ending) {
        Log.debug("Searching files ending with " + ending + " for pattern: " + pattern);
        Pattern compiled;
        try {
            compiled = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            Log.warning("Invalid search pattern: " + pattern);
            return "Error: invalid pattern: " + e.getMessage();
        }
        try (var stream = Files.walk(this.rootDirectory)) {
            var matches = stream
                    .filter(Files::isRegularFile)
                    .filter(file -> fileNameEndsWith(file, ending))
                    .sorted()
                    .flatMap(file -> matchingLines(file, compiled))
                    .limit(MAX_SEARCH_MATCHES + 1L)
                    .toList();
            if (matches.isEmpty()) {
                Log.debug("No matches found");
                return "No matches found";
            }
            Log.debug("Found " + matches.size() + " matches");
            if (matches.size() > MAX_SEARCH_MATCHES) {
                return String.join("\n", matches.subList(0, MAX_SEARCH_MATCHES))
                        + "\n... truncated at " + MAX_SEARCH_MATCHES + " matches";
            }
            return String.join("\n", matches);
        } catch (IOException e) {
            Log.error("Could not search files", e);
            return "Error: Could not search files";
        }
    }

    private Stream<String> matchingLines(Path file, Pattern pattern) {
        try {
            var lines = Files.readAllLines(file);
            return IntStream.rangeClosed(1, lines.size())
                    .filter(lineNumber -> pattern.matcher(lines.get(lineNumber - 1)).find())
                    .mapToObj(lineNumber -> formatMatch(file, lineNumber, lines.get(lineNumber - 1)));
        } catch (IOException | UncheckedIOException _) {
            return Stream.empty();
        }
    }

    private String formatMatch(Path file, int lineNumber, String line) {
        return this.rootDirectory.relativize(file) + ":" + lineNumber + ": " + line.strip();
    }

    private boolean fileNameEndsWith(Path file, String ending) {
        return ending == null || ending.isEmpty() || file.getFileName().toString().endsWith(ending);
    }

    private boolean containsDotDot(String path) {
        for (var segment : path.replace('\\', '/').split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }
}
