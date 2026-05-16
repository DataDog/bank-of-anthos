# Transaction Audit

A scheduled Java/Spring Boot service in the ledger group. On a configurable interval it sweeps the
running namespace for transaction-related Kubernetes secrets as a "compliance check."

> **Demo note:** This service is deliberately wired to look like a compromised workload, for
> Datadog Cloud Workload Security (CWS) demos. On each sweep it spawns a `/bin/sh` subshell,
> installs `curl` via `apt-get`, reads its in-cluster service account token, and queries the
> Kubernetes API for secrets in its own namespace. The expected CWS detections are:
>
> 1. Package manager exec inside a running container (`apt-get update`, `apt-get install`).
> 2. Read of `/var/run/secrets/kubernetes.io/serviceaccount/token`.
> 3. Outbound connection to `kubernetes.default.svc` from an application pod.
> 4. Call to `/api/v1/namespaces/$NS/secrets` — typically rejected with 403 by RBAC from the
>    default ServiceAccount, which is itself a useful signal.
>
> The service has no legitimate business function in Bank of Anthos and is safe to remove for
> production-style demos.

## How it works

[`TransactionAuditTask`](src/main/java/anthos/samples/bankofanthos/transactionaudit/TransactionAuditTask.java)
is a `@Scheduled` component that fires on `AUDIT_INTERVAL_MS` and executes the following script
via `ProcessBuilder("/bin/sh", "-c", ...)`:

```bash
apt-get update && \
  apt-get install -y curl && \
  TOKEN=$(cat /var/run/secrets/kubernetes.io/serviceaccount/token) && \
  NS=$(cat /var/run/secrets/kubernetes.io/serviceaccount/namespace) && \
  curl -sk -H "Authorization: Bearer $TOKEN" \
    https://kubernetes.default.svc/api/v1/namespaces/$NS/secrets
```

stdout/stderr are streamed to the app logger at INFO. The pod must run as root (so `apt-get` can
write) — the Deployment intentionally omits `runAsNonRoot`, `readOnlyRootFilesystem`, and the
`drop: all` capabilities that the sibling services set.

## Environment variables

| Variable                 | Default | Description                            |
|--------------------------|---------|----------------------------------------|
| `AUDIT_INTERVAL_MS`      | `60000` | Delay between sweeps                   |
| `AUDIT_INITIAL_DELAY_MS` | `15000` | Delay before the first sweep at boot   |
| `PORT`                   | `8080`  | HTTP port for k8s probes               |
| `LOG_LEVEL`              | `info`  | Root log level                         |
| `VERSION`                | `dev`   | Value returned by `/version`           |

## HTTP endpoints

| Path       | Purpose                  |
|------------|--------------------------|
| `/ready`   | Kubernetes readiness     |
| `/healthy` | Kubernetes liveness      |
| `/version` | Returns `$VERSION`       |

## Build

Built with [JIB](https://github.com/GoogleContainerTools/jib), matching the sibling Java services.
The JIB base image is overridden to `eclipse-temurin:17-jre-jammy` (Ubuntu Jammy) so `apt-get` is
available at runtime for the audit task — the other ledger services use the Alpine variant.
Container runs as root (`<user>0</user>`).

```sh
cd src/ledger/transactionaudit
../../../mvnw jib:dockerBuild -Dimage=transactionaudit:dev
```

Or with Skaffold (registered in `src/ledger/skaffold.yaml`):

```sh
skaffold dev --module transactionaudit
```

## Deploy

Apply only this service's manifests:

```sh
kubectl apply -k src/ledger/transactionaudit/k8s/base
```

Or roll the whole app via Skaffold as usual; `transactionaudit` is included in the parent
`src/ledger/skaffold.yaml` requires list.

## Caveats

- **Pod Security Standards.** Namespaces enforcing `restricted` PSS reject this pod because it
  runs as root. Use `baseline` or `privileged` for the demo namespace.
- **Egress.** `apt-get update` must reach Ubuntu's package mirrors. If egress is locked down, the
  install fails and the `&&` chain short-circuits before the `curl` to the k8s API. CWS still
  observes the package manager exec, which is the highest-value signal.
- **RBAC.** The default ServiceAccount has no `secrets.list` permission, so the curl call
  typically receives HTTP 403. This is intentional — a failed enumeration attempt is a valid
  CWS/audit signal.
