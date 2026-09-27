package airhacks.zsmith.lsp.control;

import java.time.Duration;

/// The server is no longer trustworthy: it could not start, stopped
/// answering, closed the connection, or answered with a protocol error.
/// The workspace discards the server on any of these and starts a fresh one
/// on the next request; the message goes verbatim into the tool result.
public class LanguageServerFailure extends RuntimeException {

    public LanguageServerFailure(String message) {
        super(message);
    }

    public LanguageServerFailure(String message, Throwable cause) {
        super(message, cause);
    }

    public static LanguageServerFailure timeout(String method, Duration timeout) {
        return new LanguageServerFailure(
                "the language server did not answer %s within %ds".formatted(method, timeout.toSeconds()));
    }

    public static LanguageServerFailure connectionClosed() {
        return new LanguageServerFailure("the language server connection closed");
    }
}
