package airhacks.zsmith.benchmark.boundary;

import java.util.LinkedHashSet;
import java.util.Set;

import airhacks.zsmith.benchmark.control.Chains;
import airhacks.zsmith.benchmark.control.TransientFaultTool;
import airhacks.zsmith.benchmark.entity.Chain;
import airhacks.zsmith.benchmark.entity.Hop;
import airhacks.zsmith.benchmark.entity.RecoveryResult;
import airhacks.zsmith.tools.boundary.Tool;

/**
 * Error-recovery benchmark for agents. The same pointer chase as
 * {@link PointerChasingBenchmark} — identical system prompt, identical tool surface — but every
 * third hop fails exactly once with a transient error and succeeds on retry. The only recovery
 * cue is the error text, so the benchmark measures whether the agent reads and reacts to tool
 * errors: retrying continues the walk, giving up truncates the secret, and a fabricated
 * fragment cannot match the seeded ground truth.
 *
 * <p>Compose it into an {@code Agent} and score the reply:
 * <pre>{@code
 * var benchmark = new ErrorRecoveryBenchmark(3);
 * var agent = new Agent("error-recoverer", benchmark.systemPrompt())
 *         .withTool(benchmark.tool())
 *         .withMaxIterations(benchmark.depth() + 3 + 20);
 * var result = benchmark.score(agent.chat("go"));
 * IO.println(result.markdownRow(agent.modelName()));
 * }</pre>
 */
public class ErrorRecoveryBenchmark {

    static final long DEFAULT_SEED = 0xC0FFEEL;
    static final int HOPS_PER_FAULT = 3;

    final Chain chain;
    final TransientFaultTool tool;
    final int faults;

    public ErrorRecoveryBenchmark(int faults) {
        this(faults, DEFAULT_SEED);
    }

    public ErrorRecoveryBenchmark(int faults, long seed) {
        this.faults = faults;
        this.chain = Chains.random(faults * HOPS_PER_FAULT, seed);
        this.tool = new TransientFaultTool(this.chain, faultyKeys(this.chain, faults));
    }

    /**
     * Every third hop of the walk fails, spread over the whole chain so recovery is demanded
     * repeatedly — not just once at a single point.
     */
    static Set<String> faultyKeys(Chain chain, int faults) {
        var keys = new LinkedHashSet<String>();
        var key = chain.start();
        for (var index = 0; !Hop.END.equals(key) && keys.size() < faults; index++) {
            if (index % HOPS_PER_FAULT == 1) {
                keys.add(key);
            }
            key = chain.hop(key).next();
        }
        return keys;
    }

    public Tool tool() {
        return this.tool;
    }

    public int depth() {
        return this.chain.depth();
    }

    public String systemPrompt() {
        return PointerChasingBenchmark.chasePrompt(this.chain.start());
    }

    public RecoveryResult score(String agentAnswer) {
        var actual = agentAnswer == null ? "" : agentAnswer.replaceAll("\\s", "");
        var passed = this.chain.secret().equals(actual);
        return new RecoveryResult(this.faults, this.chain.depth(), this.tool.calls(),
                this.tool.recovered(), passed, this.chain.secret(), actual);
    }
}
