# FX Rates Service — Injectable Faults

This service supports injectable faults for observability demos. All faults are **off by default** and must be explicitly enabled via environment variables.

---

## Stale Data Fault

Forces every read against the upstream FX provider to fail, so the service falls back to its in-memory cache and serves stale rates. Each failed read logs an `ERROR`-level message naming the upstream as the failure point.

### Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `FAULT_STALE_DATA_ENABLED` | Yes (to activate) | `false` | Set to `"true"` to enable the fault. |
| `FAULT_STALE_DATA_URL` | No | `http://192.168.1.100/v1/rates` | URL used while the fault is on. Default is an unroutable host that produces a connection failure. Override to simulate other failure modes (timeout, 5xx). |

### Behavior

When enabled, the `RatesSource.fetch()` call hits `FAULT_STALE_DATA_URL` (an unroutable host by default) instead of `FX_UPSTREAM_URL`, *even when `FX_UPSTREAM_URL` is unset*. The request fails, `RatesSourceError` is raised, and `fxrates.py` catches it, logs the error, and returns the cached rate table with `"stale": true` in the response body.

The service stays up — `/rates`, `/rates/<base>/<quote>`, `/ready`, and `/version` all continue to respond 200. The only visible signal in normal use is the `stale: true` flag in the JSON response; the *root-cause* signal is in the logs.

### Example

```yaml
env:
  - name: FAULT_STALE_DATA_ENABLED
    value: "true"
```

### What to observe in Datadog

- **Logs → fxrates**: a steady stream of `ERROR` lines reading `failed to read FX rates from upstream data source: upstream rates fetch failed (url=http://fx-provider-unreachable.invalid/v1/rates): ...`. The URL in the message tells you which dependency is the suspect.
- **APM → Services → fxrates**: outbound HTTP spans to the configured upstream URL show a 100% error rate while the service's own `/rates` span continues to return 200 — i.e. the dependency is failing but the service is masking it.
- **Response bodies**: `"stale": true` is the client-visible tell.
