# Ledger Writer — Injectable Faults

This service supports injectable faults for observability demos. All faults are **off by default** and must be explicitly enabled via environment variables.

---

## Latency Fault

Adds a configurable artificial delay to a configurable fraction of `POST /transactions` requests, simulating a slow downstream dependency or contended resource.

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `FAULT_LATENCY_ENABLED` | Yes (to activate) | `false` | Set to `"true"` to enable the latency fault. |
| `FAULT_LATENCY_MS` | No | `2000` | Base delay in milliseconds added to sampled requests. Negative values are treated as `0`. |
| `FAULT_LATENCY_JITTER_MS` | No | `0` | Maximum additional random delay in milliseconds, drawn uniformly from `[0, FAULT_LATENCY_JITTER_MS]` per sampled request. Use to spread the latency distribution. |
| `FAULT_LATENCY_RATE` | No | `1.0` | Probability between `0.0` and `1.0` that a given request is delayed. Values outside that range are clamped. |

### Behavior

When enabled, each incoming `POST /transactions` request is independently sampled against `FAULT_LATENCY_RATE`. Sampled requests sleep for `FAULT_LATENCY_MS + Random(0, FAULT_LATENCY_JITTER_MS)` before any authentication, validation, or persistence runs. Unsampled requests proceed normally.

The `/ready` and `/version` endpoints are unaffected, so Kubernetes readiness probes continue to pass while the fault is active.

The delay is applied via `Thread.sleep`, which holds the request thread for the duration. Sustained high latency under load can therefore exhaust the servlet thread pool — itself a useful demo signal.

### Example: every transaction takes 2–3 seconds

```yaml
env:
  - name: FAULT_LATENCY_ENABLED
    value: "true"
  - name: FAULT_LATENCY_MS
    value: "2000"
  - name: FAULT_LATENCY_JITTER_MS
    value: "1000"
```

### Example: 10% of transactions get a 5-second delay

```yaml
env:
  - name: FAULT_LATENCY_ENABLED
    value: "true"
  - name: FAULT_LATENCY_MS
    value: "5000"
  - name: FAULT_LATENCY_RATE
    value: "0.1"
```

### What to observe in Datadog

- **APM → Services → ledgerwriter**: p50/p95/p99 latency on `POST /transactions` jumps to roughly `FAULT_LATENCY_MS` (plus jitter); throughput may drop if the thread pool saturates.
- **APM → Trace search**: individual traces show a long, otherwise-empty gap at the start of the `addTransaction` span before any DB or downstream activity — the tell that the latency is in-process, not in a dependency.
- **RUM → Sessions / Resources**: frontend payment / transfer flows show degraded resource timings and longer time-to-interactive for users initiating transactions.
- **Dependent services**: `frontend` traces show the `ledgerwriter` span as the long pole, illustrating how a slow backend propagates upstream.
- **Monitors / SLOs**: any latency-based SLO on `ledgerwriter` should burn error budget once the fault is active.
