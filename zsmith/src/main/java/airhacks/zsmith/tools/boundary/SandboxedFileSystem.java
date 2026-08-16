package airhacks.zsmith.tools.boundary;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import airhacks.zsmith.logging.control.Log;
import airhacks.zsmith.tools.control.IgnoredDirectories;
import airhacks.zsmith.tools.control.LineRange;

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
        return readFile(relativePath, LineRange.wholeFile(), false);
    }

    /// Reads `relativePath`, restricted to `range` and optionally prefixed with
    /// absolute line numbers. A sliced read is headed by the window it covers and
    /// the file's total line count, so a reader can tell whether more remains.
    public String readFile(String relativePath, LineRange range, boolean numbered) {
        Log.debug("Reading file: " + relativePath + " " + range);
        Path resolved;
        try {
            resolved = resolve(relativePath);
        } catch (IllegalArgumentException e) {
            Log.error("Invalid path: " + relativePath);
            throw e;
        }
        try {
            if (range.coversWholeFile() && !numbered) {
                var content = Files.readString(resolved);
                Log.debug("Read " + content.length() + " chars from " + relativePath);
                return content;
            }
            return selectLines(Files.readAllLines(resolved), range, numbered);
        } catch (java.nio.file.NoSuchFileException e) {
            Log.warning("File not found: " + relativePath);
            return "Error: File not found";
        } catch (IOException e) {
            Log.error("Could not read file: " + relativePath, e);
            return "Error: Could not read file";
        }
    }

    private String selectLines(List<String> lines, LineRange range, boolean numbered) {
        var totalLines = lines.size();
        if (range.startsBeyond(totalLines)) {
            return "Error: offset %d is beyond the last line, the file has %d lines"
                    .formatted(range.offset(), totalLines);
        }
        var lastLine = range.lastLine(totalLines);
        var selected = IntStream.rangeClosed(range.offset(), lastLine)
                .mapToObj(lineNumber -> numbered
                        ? "%6d\t%s".formatted(lineNumber, lines.get(lineNumber - 1))
                        : lines.get(lineNumber - 1))
                .collect(Collectors.joining("\n"));
        if (range.coversWholeFile()) {
            return selected;
        }
        return "[lines %d-%d of %d]\n%s".formatted(range.offset(), lastLine, totalLines, selected);
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

    /// Replaces `target` with `replacement` in the file at `relativePath`.
    /// Matching is exact and verbatim — no whitespace normalization — and the
    /// target must occur exactly once unless `replaceAll` widens the edit to
    /// every occurrence. Everything outside the match is preserved as-is.
    public String editFile(String relativePath, String target, String replacement, boolean replaceAll) {
        Log.debug("Editing file: " + relativePath);
        Path resolved;
        try {
            resolved = resolve(relativePath);
        } catch (IllegalArgumentException e) {
            Log.error("Invalid path: " + relativePath);
            throw e;
        }
        if (target.isEmpty()) {
            return "Error: old_string must not be empty, use write_file to create content";
        }
        if (target.equals(replacement)) {
            return "Error: old_string and new_string are identical, nothing to change";
        }
        String content;
        try {
            content = Files.readString(resolved);
        } catch (java.nio.file.NoSuchFileException e) {
            Log.warning("File not found: " + relativePath);
            return "Error: File not found";
        } catch (IOException e) {
            Log.error("Could not read file: " + relativePath, e);
            return "Error: Could not read file";
        }
        var occurrences = countOccurrences(content, target);
        if (occurrences == 0) {
            Log.warning("old_string not found in: " + relativePath);
            return "Error: old_string not found in " + relativePath
                    + ", matching is exact including whitespace, re-read the file and retry";
        }
        if (occurrences > 1 && !replaceAll) {
            Log.warning("old_string ambiguous in " + relativePath + ": " + occurrences + " occurrences");
            return "Error: old_string occurs " + occurrences + " times in " + relativePath
                    + ", add surrounding context to make it unique or pass replace_all=\"true\"";
        }
        try {
            Files.writeString(resolved, content.replace(target, replacement));
        } catch (IOException e) {
            Log.error("Could not write file: " + relativePath, e);
            return "Error: Could not write file";
        }
        Log.debug("Replaced " + occurrences + " occurrence(s) in " + relativePath);
        return replaceAll
                ? "Replaced " + occurrences + " occurrence" + (occurrences == 1 ? "" : "s") + " in: " + relativePath
                : "Edited file: " + relativePath;
    }

    private int countOccurrences(String content, String target) {
        var count = 0;
        var index = content.indexOf(target);
        while (index >= 0) {
            count++;
            index = content.indexOf(target, index + target.length());
        }
        return count;
    }

    public String listFiles() {
        return listFiles("");
    }

    public String listFiles(String ending) {
        Log.debug("Listing files ending with: " + ending);
        try {
            var files = traversableFiles().stream()
                    .filter(file -> fileNameEndsWith(file, ending))
                    .map(this.rootDirectory::relativize)
                    .map(Path::toString)
                    .toList();
            if (files.isEmpty()) {
                Log.debug("No files ending with " + ending + " found");
                return ending.isEmpty()
                        ? "No files found in sandbox"
                        : "No files ending with " + ending + " found";
            }
            Log.debug("Found " + files.size() + " files ending with " + ending);
            return String.join("\n", files);
        } catch (IOException e) {
            Log.error("Could not list files", e);
            return "Error: Could not list files";
        }
    }

    /// Files whose name or root-relative path matches `globPattern`. A plain
    /// name pattern (no separator) matches at any depth — "Dockerfile*" finds
    /// src/main/docker/Dockerfile.jvm — while a pattern with separators is
    /// matched against the relative path.
    public String findFiles(String globPattern) {
        Log.debug("Finding files matching: " + globPattern);
        PathMatcher matcher;
        try {
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);
        } catch (IllegalArgumentException | UnsupportedOperationException e) {
            Log.warning("Invalid glob pattern: " + globPattern);
            return "Error: invalid pattern: " + e.getMessage();
        }
        try {
            var files = traversableFiles().stream()
                    .map(this.rootDirectory::relativize)
                    .filter(path -> matcher.matches(path) || matcher.matches(path.getFileName()))
                    .map(Path::toString)
                    .toList();
            if (files.isEmpty()) {
                Log.debug("No files matching " + globPattern + " found");
                return "No files matching " + globPattern + " found";
            }
            Log.debug("Found " + files.size() + " files matching " + globPattern);
            return String.join("\n", files);
        } catch (IOException e) {
            Log.error("Could not find files", e);
            return "Error: Could not find files";
        }
    }

    /// Every regular file below the root, in stable order, with the
    /// [IgnoredDirectories] subtrees pruned rather than walked and discarded.
    private List<Path> traversableFiles() throws IOException {
        var ignored = IgnoredDirectories.resolve();
        var files = new ArrayList<Path>();
        Files.walkFileTree(this.rootDirectory, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                var isRoot = directory.equals(SandboxedFileSystem.this.rootDirectory);
                if (!isRoot && ignored.contains(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile()) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) {
                Log.warning("Skipping unreadable path: " + file);
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.naturalOrder());
        return files;
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
        try {
            var matches = traversableFiles().stream()
                    .filter(file -> fileNameEndsWith(file, ending))
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
