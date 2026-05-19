# Transaction History — Injectable Faults

This service supports injectable faults for observability demos. All faults are **off by default** and must be explicitly enabled via environment variables.

---

## Memory Fault

Simulates excessive memory consumption by allocating and holding a configurable amount of memory in a background thread.

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `FAULT_MEMORY_ENABLED` | Yes (to activate) | `false` | Set to `"true"` to enable the memory fault. |
| `FAULT_MEMORY_MB` | No | `100` | Total additional memory in megabytes to allocate and hold. |
| `FAULT_MEMORY_GROWTH_INTERVAL_MS` | No | `500` | Milliseconds between each 1 MB allocation step — controls how quickly memory grows to the target. |
| `FAULT_MEMORY_STARTUP_DELAY_SECONDS` | No | `0` | Seconds to wait after application startup before allocation begins. Useful for letting the service stabilize / be observed at baseline first. |

### Behavior

When enabled, a background thread allocates 1 MB chunks at the configured interval until the total reaches `FAULT_MEMORY_MB`. The memory is held for the lifetime of the process, simulating a leak or excessive resident set size.

### Example: slow 200 MB leak

```yaml
env:
  - name: FAULT_MEMORY_ENABLED
    value: "true"
  - name: FAULT_MEMORY_MB
    value: "200"
  - name: FAULT_MEMORY_GROWTH_INTERVAL_MS
    value: "1000"
```

### What to observe in Datadog

- **Infrastructure → Containers**: `container.memory.usage` climbs steadily until the target is reached.
- **APM → Services → transactionhistory**: JVM heap metrics (`jvm.heap_memory`, `jvm.non_heap_memory`) show elevated usage.
- **Monitors / Anomaly Detection**: Memory growth rate triggers anomaly alerts if configured.

---

## Disk Fault

Simulates a chatty writer by repeatedly overwriting the same bytes of a single file with synchronous writes. The file does not grow — the goal is high write IOPS / throughput, not disk consumption.

### Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `FAULT_DISK_ENABLED` | Yes (to activate) | `false` | Set to `"true"` to enable the disk fault. |
| `FAULT_DISK_FILE_PATH` | No | *(random temp file)* | Path of the file to repeatedly overwrite. If unset, a randomly-named file is created in the system temp directory. |
| `FAULT_DISK_WRITE_SIZE_KB` | No | `64` | Bytes (in KB) written per iteration. The file is truncated to this size and the same bytes are rewritten each loop. |
| `FAULT_DISK_WRITE_INTERVAL_MS` | No | `10` | Milliseconds to sleep between writes. Lower values produce higher IOPS. |
| `FAULT_DISK_LOG_MESSAGE` | No | *(no log)* | If set to a non-empty string, this message is emitted at `INFO` level after each successful write. Useful for correlating disk-IO spikes with log volume in Datadog. |

### Behavior

When enabled, a background thread opens the target file in synchronous-write mode (`rwd`) and loops forever: seek to 0, write `FAULT_DISK_WRITE_SIZE_KB` bytes, sleep `FAULT_DISK_WRITE_INTERVAL_MS`, repeat. Synchronous mode forces each write to be flushed to the underlying device, so the IO is visible to disk-level metrics rather than absorbed by the page cache. The file is deleted on shutdown.

### Example: ~6,400 KB/s write rate

```yaml
env:
  - name: FAULT_DISK_ENABLED
    value: "true"
  - name: FAULT_DISK_FILE_PATH
    value: "/tmp/transaction-cache.dat"
  - name: FAULT_DISK_WRITE_SIZE_KB
    value: "64"
  - name: FAULT_DISK_WRITE_INTERVAL_MS
    value: "10"
```

### What to observe in Datadog

- **Infrastructure → Hosts**: `system.io.w_s` (writes/sec) and `system.io.wkb_s` (write KB/s) on the affected device spike.
- **Live Processes**: the `transactionhistory` JVM process shows elevated `process.io.write_bytes`.
- **Cloud Workload Security / File Integrity Monitoring**: high-frequency `open`/`write` events against the target path — useful for students to identify *which file* is being hammered.
- **Disk usage stays flat** — this is the tell that distinguishes a write-storm from a leak.
