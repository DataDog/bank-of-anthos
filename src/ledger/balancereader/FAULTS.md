# Balance Reader — Injectable Faults

This service supports injectable faults for observability demos. All faults are **off by default** and must be explicitly enabled via environment variables.

---

## Unhandled Exception Fault

Causes a configurable fraction of `/balances/<accountid>` requests to throw an unhandled `RuntimeException`. The exception is not caught by the controller, so the framework's default error handler surfaces it as a `500 Internal Server Error` with a stack trace logged at `ERROR` level — the signature of a real, unintended bug rather than a clean error response.

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `FAULT_UNHANDLED_EXCEPTION_ENABLED` | Yes (to activate) | `false` | Set to `"true"` to enable the unhandled-exception fault. |
| `FAULT_UNHANDLED_EXCEPTION_RATE` | No | `0.5` | Probability between `0.0` and `1.0` that a given balance request throws. Values outside that range are clamped. |
| `FAULT_UNHANDLED_EXCEPTION_MESSAGE` | No | `unexpected error retrieving account balance` | Message attached to the thrown `RuntimeException`. Appears in logs and APM error tags. |

### Behavior

When enabled, each incoming `/balances/<accountid>` request is independently sampled against `FAULT_UNHANDLED_EXCEPTION_RATE`. Sampled requests throw a `RuntimeException` before authentication or cache lookup; unsampled requests proceed normally. The check is per-request and not sticky — a given client may see a mix of successes and failures.

The exception propagates out of the controller and is handled by Spring's default error mapping, producing a `500` response and an `ERROR`-level log entry that includes a stack trace pointing at `UnhandledExceptionFault.maybeThrow`.

The `/healthy`, `/ready`, and `/version` endpoints are unaffected, so Kubernetes liveness and readiness probes continue to pass while the fault is active.

### Example: 25% of requests throw

```yaml
env:
  - name: FAULT_UNHANDLED_EXCEPTION_ENABLED
    value: "true"
  - name: FAULT_UNHANDLED_EXCEPTION_RATE
    value: "0.25"
  - name: FAULT_UNHANDLED_EXCEPTION_MESSAGE
    value: "balance lookup failed: cache miss"
```

### What to observe in Datadog

- **APM → Services → balancereader**: error rate climbs to roughly `FAULT_UNHANDLED_EXCEPTION_RATE`; affected spans are flagged as errors and tagged with the exception type (`java.lang.RuntimeException`) and message.
- **APM → Error Tracking**: a new issue groups the failures by stack trace, with the top frame in `UnhandledExceptionFault.maybeThrow`.
- **Logs → Service: balancereader**: stack traces appear at `ERROR` level — the distinguishing signal vs. a clean error-response fault.
- **RUM → Sessions / Resources**: frontend balance fetches show `error` resources and degraded session quality for users who hit the failing requests.
- **Dependent services**: `frontend` traces show downstream `balancereader` spans flagged as errors, illustrating how a backend fault propagates upstream.
