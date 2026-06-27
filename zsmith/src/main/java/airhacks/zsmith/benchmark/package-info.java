/**
 * Loop-following benchmark for agents, built around <em>pointer chasing</em>.
 *
 * <p><strong>Pointer chasing</strong> is the established term (from systems and computer
 * architecture) for traversing a chain in which each step's address is read from the value
 * loaded in the previous step: {@code node = node.next}. The defining property is a
 * <em>serial data dependency</em> — you cannot compute hop N without first holding the
 * result of hop N-1, so the walk can be neither parallelized nor predicted ahead of time.
 * Hardware benchmarks use it to measure memory latency because it defeats prefetching; here
 * it serves the same purpose against an agent: it forces a genuine, ordered tool-calling loop
 * instead of a batch of independent calls.
 *
 * <p>This component generates a hidden chain of {@link airhacks.zsmith.benchmark.entity.Hop}s
 * with random, unguessable keys (see {@link airhacks.zsmith.benchmark.control.Chains}). The
 * agent starts with one key and must call the tool repeatedly — each result reveals the next
 * key and one fragment of a secret — until it reaches the terminal marker. Because the next
 * key is only knowable from the previous tool result, the agent has to walk all {@code depth}
 * hops in order; one skipped or reordered hop corrupts the reconstructed secret, making the
 * outcome an objective pass/fail rather than a judged answer.
 *
 * <p>The single entry point is {@link airhacks.zsmith.benchmark.boundary.PointerChasingBenchmark}:
 * compose its {@code tool()} into an {@code Agent}, run it, and {@code score()} the reply.
 */
package airhacks.zsmith.benchmark;
