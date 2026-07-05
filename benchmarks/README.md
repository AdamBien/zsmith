# Agent Benchmarks

Executable benchmarks that score an LLM-driven agent's tool-calling behavior against known,
seeded ground truth — no LLM judge. Each script builds a hidden task, runs a zsmith `Agent`
against it, and prints a one-line result. The benchmarks target **orthogonal axes** so their
results can disagree: a model can pass one and fail another.

## Results

Every run prints exactly one normalized markdown table row on stdout — same columns for
every benchmark — so after a run (or a sweep) the rows paste directly into this table.
Failure details and extra signals go to stderr, never into the row.

| Benchmark | Size | Calls | Turns | Result |
|-----------|------|-------|-------|--------|
| loop | 50 | 50 | – | PASS |
| parallelism | 8 | 8 | 1 | PASS |

- **Benchmark** — `loop` (pointer chasing) or `parallelism` (parallel discrimination)
- **Size** — the task size knob: chain `depth` for loop, independent `tasks` for parallelism
- **Calls** — total tool calls the agent issued
- **Turns** — agent turns that issued tool calls; loop does not track turns (`–`)
- **Result** — `PASS`/`FAIL`: secret match (loop), all values retrieved (parallelism)

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
| loop | 50 | 50 | – | PASS |
| loop | 100 | 63 | – | FAIL |
```

Compare `Calls` to `Size`: more calls means the agent wandered or retried; fewer means it
stopped early (often narrating or hallucinating hops instead of calling the tool). Both are
loop-following failures. On `FAIL`, the expected/actual secret mismatch is printed to stderr.

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
| parallelism | 8 | 8 | 1 | PASS |   # batched — ideal
| parallelism | 8 | 8 | 8 | PASS |   # serialized
```

`Turns` near 1 means the agent batched the independent calls; `Turns` near `Size` means it
serialized them. The measured `maxConcurrency` is printed to stderr. A model whose provider
never emits multiple tool calls per turn reads as fully serial — a valid result, not a bug.

## Reproducibility

Every task is seeded, so a given size reproduces across runs; model non-determinism is the only
variance. Running both benchmarks on the same model is the point: pointer chasing forbids
parallelism, parallel discrimination rewards it, so the pair reveals whether a model can
parallelize when allowed.
