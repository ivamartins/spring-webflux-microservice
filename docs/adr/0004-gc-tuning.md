# ADR 0004: G1GC tuning

- **Status:** Accepted
- **Date:** 2024-01

## Decision

Use **G1GC** with `MaxGCPauseMillis=200`, `UseStringDeduplication=true`, and `+HeapDumpOnOutOfMemoryError`. GC logs to a rotating file at `/tmp/gc.log`.

## Rationale

- G1 is the default collector in Java 21 and is well-suited to heap sizes 4GB-32GB.
- A 200ms pause target keeps p99 within SLO for a low-latency API.
- String deduplication cuts heap pressure for JSON workloads (orders are mostly text).
- Heap dumps on OOM are invaluable for post-mortem.

## How to apply

Pass these via `JAVA_TOOL_OPTIONS` in the Dockerfile / Kubernetes pod spec, or via the `JAVA_OPTS` env var in CI:

```
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+UseStringDeduplication
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heap.hprof
-Xlog:gc*,gc+heap=info,gc+age=trace,safepoint:file=/tmp/gc.log:time,uptime,level,tags:filecount=5,filesize=50m
```

## Profiling

- `jcmd <pid> GC.heap_info` — quick heap snapshot
- `jcmd <pid> GC.heap_dump <file>` — full heap dump
- VisualVM / async-profiler for CPU and allocation profiling
- JDK Mission Control for continuous profiling in prod (low overhead)

## Alternatives considered

- **ZGC**: lower pause, but higher CPU overhead. Worth re-evaluating for > 100ms p99 requirements.
- **Parallel GC**: rejected — high pauses unacceptable for an API.
