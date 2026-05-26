# fxrates

Standalone FX rates web service. Independent of the rest of Bank of Anthos — no DB, no JWT, no calls to other services. Used for isolated observability demos.

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/rates` | Returns the full USD-based rate table plus `stale` flag and `fetched_at` timestamp. |
| GET | `/rates/<base>/<quote>` | Returns the exchange rate from `base` to `quote` (e.g. `/rates/EUR/JPY`). |
| GET | `/ready` | Readiness probe. |
| GET | `/version` | Service version. |

The service reads its rate table from an upstream FX provider over HTTP. On success the cache is refreshed and the response carries `"stale": false`. On failure the cached table is served with `"stale": true` and an `ERROR` line is logged. If no upstream is configured, no fetch is attempted and the built-in seed table is served as fresh.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | Port the HTTP server listens on. |
| `VERSION` | `dev` | Value returned by `/version`. |
| `LOG_LEVEL` | `INFO` | Python logging level. |
| `FX_UPSTREAM_URL` | *(unset)* | URL of the upstream FX rate provider. If unset, no upstream fetch is attempted and the seed table is served as fresh. |
| `FX_UPSTREAM_TIMEOUT_SECONDS` | `2.0` | Per-request timeout for upstream fetches. |

Fault-injection variables are documented separately in [FAULTS.md](FAULTS.md).

## Build

```sh
docker build -t fxrates:dev .
```

## Run

Default config (no upstream configured — serves the seed table directly):

```sh
docker run --rm -p 8080:8080 fxrates:dev
```

Pointing at a real upstream:

```sh
docker run --rm -p 8080:8080 \
  -e FX_UPSTREAM_URL=https://api.example.com/v1/rates \
  fxrates:dev
```

## Faults

See [FAULTS.md](FAULTS.md). The stale-data fault is toggled via `FAULT_STALE_DATA_ENABLED=true`.
