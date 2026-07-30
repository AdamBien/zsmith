package airhacks.zsmith.agentcore.control;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.sun.net.httpserver.Headers;

public class RuntimeSessions {

    public static final String RUNTIME_HEADER = "X-Amzn-Bedrock-AgentCore-Runtime-Session-Id";
    public static final String FALLBACK_HEADER = "X-Session-Id";

    final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public String resolveOrCreate(Headers requestHeaders) {
        var requested = requestHeaders.getFirst(RUNTIME_HEADER);
        if (requested == null || requested.isBlank()) {
            requested = requestHeaders.getFirst(FALLBACK_HEADER);
        }
        if (requested == null || requested.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requested;
    }

    public ReentrantLock lockFor(String sessionId) {
        return this.locks.computeIfAbsent(sessionId, id -> new ReentrantLock());
    }
}
