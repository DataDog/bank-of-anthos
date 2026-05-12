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

---

## Log Flood Fault

Emits a configurable number of varied, framework-style log lines on every (or a sampled fraction of) `POST /transactions` requests, simulating a misconfigured verbose logger left enabled in production. Messages are drawn from a pool of templates resembling Hibernate SQL, HikariCP pool stats, RestTemplate calls, JWT decode, GC pauses, etc., so the flood looks like real noise rather than synthetic output.

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `FAULT_LOG_FLOOD_ENABLED` | Yes (to activate) | `false` | Set to `"true"` to enable the log-flood fault. |
| `FAULT_LOG_FLOOD_COUNT` | No | `200` | Number of log lines emitted per sampled request. Negative values are treated as `0`. |
| `FAULT_LOG_FLOOD_LEVEL` | No | `DEBUG` | Log level for emitted lines. Accepts standard Log4j levels (`TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`). Unknown values fall back to `DEBUG`. |
| `FAULT_LOG_FLOOD_RATE` | No | `1.0` | Probability between `0.0` and `1.0` that a given request triggers the flood. Values outside that range are clamped. |

### Behavior

When enabled, each incoming `POST /transactions` request is independently sampled against `FAULT_LOG_FLOOD_RATE`. Sampled requests emit `FAULT_LOG_FLOOD_COUNT` log lines at `FAULT_LOG_FLOOD_LEVEL` before the request returns. Templates and field values (account numbers, UUIDs, IPs, status codes, etc.) are randomized per line so logs don't collapse under deduplication.

The `/ready` and `/version` endpoints are unaffected, so Kubernetes readiness probes continue to pass while the fault is active.

Log emission is synchronous, so very high `FAULT_LOG_FLOOD_COUNT` values will also add latency to the request and can pressure the logging pipeline (Datadog Agent log intake, storage / indexing costs).

### Example: 200 DEBUG lines on every transaction

```yaml
env:
  - name: FAULT_LOG_FLOOD_ENABLED
    value: "true"
```

### Example: 1000 WARN lines on 10% of transactions

```yaml
env:
  - name: FAULT_LOG_FLOOD_ENABLED
    value: "true"
  - name: FAULT_LOG_FLOOD_COUNT
    value: "1000"
  - name: FAULT_LOG_FLOOD_LEVEL
    value: "WARN"
  - name: FAULT_LOG_FLOOD_RATE
    value: "0.1"
```

### What to observe in Datadog

- **Logs → Service: ledgerwriter**: log volume spikes sharply; the flood lines are tagged with the configured level and look like real framework output (Hibernate, HikariCP, RestTemplate, JWT, GC, etc.).
- **Logs → Analytics / Patterns**: a handful of high-volume patterns dominate the service's logs — the tell that a single source is generating the flood.
- **APM → Services → ledgerwriter**: at high `FAULT_LOG_FLOOD_COUNT`, p95/p99 latency on `POST /transactions` rises as synchronous log writes block the request thread.
- **Usage / Billing**: ingested log volume and indexed log count for `ledgerwriter` climb, illustrating the cost impact of accidental verbose logging in production.
- **Monitors / SLOs**: any log-volume anomaly monitor on `ledgerwriter` should trigger; latency-based SLOs may also burn budget at high counts.
