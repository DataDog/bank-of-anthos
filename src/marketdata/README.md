# marketdata

Standalone market-data web service. Independent of the rest of Bank of Anthos — no DB, no JWT, no calls to other services. Used for isolated observability demos.

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/quote/<symbol>` | Returns a current quote for the given symbol (e.g. `/quote/AAPL`). |
| GET | `/quotes` | Returns quotes for all tracked symbols. |
| GET | `/ready` | Readiness probe. |
| GET | `/version` | Service version. |

Quotes are generated in-process from a fixed seed table by applying a small random walk per call. There is no external data source.

## Configuration

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | Port the HTTP server listens on. |
| `VERSION` | `dev` | Value returned by `/version`. |
| `LOG_LEVEL` | `INFO` | Python logging level. |

Fault-injection variables are documented separately in [FAULTS.md](FAULTS.md).

## Build

```sh
docker build -t marketdata:dev .
```

## Run

```sh
docker run --rm -p 8080:8080 marketdata:dev
```

## Faults

See [FAULTS.md](FAULTS.md). The latency fault is toggled via `FAULT_LATENCY_ENABLED=true`.
