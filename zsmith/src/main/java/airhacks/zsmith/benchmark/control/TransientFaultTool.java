package airhacks.zsmith.benchmark.control;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import airhacks.zsmith.json.JSONObject;

import airhacks.zsmith.benchmark.entity.Chain;
import airhacks.zsmith.tools.boundary.Tool;

/**
 * Decorates {@link PointerChasingTool} with seeded transient faults: each faulty key fails
 * exactly once, then succeeds on retry. The recovery cue lives only in the error text — the
 * tool name, description, and schema are the delegate's, unchanged, so the benchmark measures
 * the agent's reaction to an error result, not obedience to an upfront warning.
 */
public class TransientFaultTool implements Tool {

    static final int FAILURES_PER_KEY = 1;

    final PointerChasingTool delegate;
    final Set<String> faultyKeys;
    final Map<String, Integer> attempts;
    final AtomicInteger calls;

    public TransientFaultTool(Chain chain, Set<String> faultyKeys) {
        this.delegate = new PointerChasingTool(chain);
        this.faultyKeys = faultyKeys;
        this.attempts = new ConcurrentHashMap<>();
        this.calls = new AtomicInteger();
    }

    public int calls() {
        return this.calls.get();
    }

    /** Faulty keys the agent called again after the failure — the recovery count. */
    public int recovered() {
        return (int) this.faultyKeys.stream()
                .filter(key -> this.attempts.getOrDefault(key, 0) > FAILURES_PER_KEY)
                .count();
    }

    @Override
    public String toolName() {
        return this.delegate.toolName();
    }

    @Override
    public String description() {
        return this.delegate.description();
    }

    @Override
    public JSONObject inputSchema() {
        return this.delegate.inputSchema();
    }

    @Override
    public String execute(JSONObject input) {
        this.calls.incrementAndGet();
        var key = input.getString(PointerChasingTool.Field.key.name());
        var attempt = this.attempts.merge(key, 1, Integer::sum);
        if (this.faultyKeys.contains(key) && attempt <= FAILURES_PER_KEY) {
            return "ERROR: transient failure for key '" + key + "' — call again with the same key.";
        }
        return this.delegate.execute(input);
    }
}
