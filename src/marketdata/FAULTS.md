# Market Data Service — Injectable Faults

This service supports injectable faults for observability demos. All faults are **off by default** and must be explicitly enabled via environment variables.

---

## Latency Fault

Adds a configurable artificial delay to a configurable fraction of quote computations. The delay is applied inside `_compute_quote()` — the function that every `/quote/<symbol>` and `/quotes` request runs — so the slowness shows up *inside* the service, not on any dependency.

### Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `FAULT_LATENCY_ENABLED` | Yes (to activate) | `false` | Set to `"true"` to enable the latency fault. |
| `FAULT_LATENCY_MS` | No | `1500` | Base delay in milliseconds applied to sampled requests. Negative values are treated as `0`. |
| `FAULT_LATENCY_JITTER_MS` | No | `500` | Maximum additional random delay drawn uniformly from `[0, FAULT_LATENCY_JITTER_MS]` per sampled call. |
| `FAULT_LATENCY_RATE` | No | `1.0` | Probability between `0.0` and `1.0` that a given call is delayed. Clamped to `[0, 1]`. |

### Behavior

When enabled, each call to `_compute_quote()` is independently sampled against `FAULT_LATENCY_RATE`. Sampled calls sleep for `FAULT_LATENCY_MS + Random(0, FAULT_LATENCY_JITTER_MS)` before computing the quote. `/quotes` aggregates over all symbols, so the per-call delay multiplies — that endpoint will appear *especially* slow.

`/ready` and `/version` are unaffected, so Kubernetes readiness probes stay green.

### Example: every quote takes 1.5–2 seconds

```yaml
env:
  - name: FAULT_LATENCY_ENABLED
    value: "true"
```

### Example: 20% of quotes get a 3-second delay

```yaml
env:
  - name: FAULT_LATENCY_ENABLED
    value: "true"
  - name: FAULT_LATENCY_MS
    value: "3000"
  - name: FAULT_LATENCY_JITTER_MS
    value: "0"
  - name: FAULT_LATENCY_RATE
    value: "0.2"
```

### What to observe in Datadog

- **APM → Services → marketdata**: p50/p95/p99 on `/quote/<symbol>` rise to roughly `FAULT_LATENCY_MS`; `/quotes` rises proportionally to the number of symbols (~10x).
- **APM → Trace search**: the slow span is the in-process `_compute_quote` work, not an outbound call. No dependency error rates change — this is purely a slow code path.
- **Logs**: no error logs are emitted. The only signal is latency.
