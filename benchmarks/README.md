# Agent Benchmarks

Executable benchmarks that score an LLM-driven agent's tool-calling behavior against known,
seeded ground truth — no LLM judge. Each script builds a hidden task, runs a zsmith `Agent`
against it, and prints a one-line result. The benchmarks target **orthogonal axes** so their
results can disagree: a model can pass one and fail another.

## Results

Every run prints exactly one normalized markdown table row on stdout — same columns for
every benchmark — and copies it to the system clipboard, so after a run the row pastes
directly into this table. Failure details and extra signals go to stderr, never into the
row. In a sweep each run overwrites the clipboard, so copy the sweep's rows from the
terminal instead.

| Benchmark | Model | Size | Calls | Turns | Result |
|-----------|-------|------|-------|-------|--------|
| loop | claude-opus-4-8 | 50 | 50 | – | PASS |
| parallelism | claude-opus-4-8 | 8 | 8 | 1 | PASS |
| recovery | claude-opus-4-8 | 3 | 12 | – | PASS |

- **Benchmark** — `loop` (pointer chasing), `parallelism` (parallel discrimination), or
  `recovery` (transient-fault recovery)
- **Model** — the model reported by the LLM responses: the one actually served, which can
  differ from the configured one (529 fallback, lightmetal's own config)
- **Size** — the task size knob: chain `depth` for loop, independent `tasks` for parallelism,
  injected `faults` for recovery
- **Calls** — total tool calls the agent issued
- **Turns** — agent turns that issued tool calls; the serial benchmarks do not track turns (`–`)
- **Result** — `PASS`/`FAIL`: secret match (loop, recovery), all values retrieved (parallelism)

A sweep appends ready-to-paste rows:

```
for d in 10 25 50 100 200; do ./agentLoopBenchmark $d; done
```

## Prerequisites

Java 25+, and a built zsmith. The scripts load `../zsmith/zbo/zsmith.jar` and `lightmetal.jar`,
so build first from the `zsmith/` directory:

```
cd ../zsmith && zb.sh
```

Inference runs in-process through LightMetal (the local model configured for zsmith); no API
key is required.

## agentLoopBenchmark — pointer chasing (loop-following)

Measures stamina at a long, *serial* tool loop. The agent starts with one key and calls
`follow_pointer` repeatedly — each result reveals the next key plus one fragment of a secret —
until the terminal marker, then reassembles the fragments in order. Each hop's key is read from
the previous result, so the walk has a **serial data dependency**: it cannot be parallelized or
predicted, forcing an ordered loop of exactly `depth` calls. One skipped or reordered hop
corrupts the secret.

```mermaid
flowchart LR
    S[start key] --> F[follow_pointer]
    F -->|fragment + next key| F
    F -->|next = END| A[assemble secret]
```

```
./agentLoopBenchmark         # default depth 50
for d in 10 25 50 100 200; do ./agentLoopBenchmark $d; done
```

```
| loop | claude-opus-4-8 | 50 | 50 | – | PASS |
| loop | claude-opus-4-8 | 100 | 63 | – | FAIL |
```

Compare `Calls` to `Size`: more calls means the agent wandered or retried; fewer means it
stopped early (often narrating or hallucinating hops instead of calling the tool). Both are
loop-following failures. On `FAIL`, the expected/actual secret mismatch is printed to stderr.

## agentErrorRecoveryBenchmark — transient-fault recovery (robustness)

The same pointer chase as the loop benchmark — identical system prompt, identical tool
surface — but every third hop fails exactly once with
`ERROR: transient failure for key '…' — call again with the same key.` and succeeds on
retry. The only recovery cue is that error text: nothing in the prompt or the tool
description announces that failures can happen, so the benchmark measures whether the agent
**reads and reacts to tool errors**. Retrying continues the walk; giving up truncates the
secret; a fabricated fragment cannot match the seeded ground truth. `faults` is the size
knob, and the chain is `3 × faults` hops long so recovery is demanded repeatedly, spread
over the whole walk.

```mermaid
flowchart LR
    S[start key] --> F[follow_pointer]
    F -->|fragment + next key| F
    F -->|ERROR: transient| R[retry same key]
    R --> F
    F -->|next = END| A[assemble secret]
```

```
./agentErrorRecoveryBenchmark          # default 3 faults (9 hops)
for f in 2 4 8; do ./agentErrorRecoveryBenchmark $f; done
```

```
| recovery | claude-opus-4-8 | 3 | 12 | – | PASS |   # 9 hops + 3 retries — ideal
| recovery | claude-opus-4-8 | 3 | 5 | – | FAIL |    # gave up at the second fault
```

Ideal `Calls` = hops + faults: each fault costs exactly one retry. `recovered=X/faults` on
stderr shows how many injected failures were retried past; `Calls` well below hops + faults
means the agent abandoned the walk or hallucinated past an error — the mismatched secret
exposes both.

## agentParallelismBenchmark — parallel discrimination (independence)

The inverse axis. The agent is given `tasks` **independent** `id → value` pairs with every id
listed up front, so there is no data dependency. A `lookup` tool returns each value. An agent
that recognizes independence issues all calls in **one turn**; one that needlessly serializes
spreads them across `tasks` turns. The tool runs in parallel and gauges its own concurrency, so
the headline signal is `Turns` vs `Calls`, not a correctness match — `PASS` is only a gate
confirming all lookups were actually performed.

```mermaid
flowchart LR
    R[tasks ids known up front] --> L1[lookup]
    R --> L2[lookup]
    R --> L3[lookup]
    L1 & L2 & L3 --> A[report all values]
```

```
./agentParallelismBenchmark        # default 8 independent lookups
for k in 4 8 16 32; do ./agentParallelismBenchmark $k; done
```

```
| parallelism | claude-opus-4-8 | 8 | 8 | 1 | PASS |   # batched — ideal
| parallelism | claude-opus-4-8 | 8 | 8 | 8 | PASS |   # serialized
```

`Turns` near 1 means the agent batched the independent calls; `Turns` near `Size` means it
serialized them. The measured `maxConcurrency` is printed to stderr. A model whose provider
never emits multiple tool calls per turn reads as fully serial — a valid result, not a bug.

## Reproducibility

Every task is seeded, so a given size reproduces across runs; model non-determinism is the only
variance. Running both benchmarks on the same model is the point: pointer chasing forbids
parallelism, parallel discrimination rewards it, so the pair reveals whether a model can
parallelize when allowed.
