# Bank of Anthos — Claude Context

## What this repo is

Bank of Anthos is a sample HTTP-based web banking app used as a Datadog APM/RUM instrumentation demo. It runs on GKE. The app is already deployed and accessible via the LoadBalancer IP of the `frontend` service:

```sh
kubectl get service frontend | awk '{print $4}'
# visit http://<EXTERNAL_IP>
```

## Service map

| Service | Language | Role |
|---|---|---|
| frontend | Python (Flask/Gunicorn) | UI — login, home, payments |
| userservice | Python (Flask/Gunicorn) | Auth, JWT issuance |
| contacts | Python (Flask/Gunicorn) | Contact list per user |
| ledgerwriter | Java (Spring Boot) | Accepts + validates transactions |
| balancereader | Java (Spring Boot) | Cached balance reads |
| transactionhistory | Java (Spring Boot) | Cached transaction history |
| ledger-db | PostgreSQL | Transaction ledger |
| accounts-db | PostgreSQL | User accounts |
| loadgenerator | Python (Locust) | Synthetic traffic |

Java services are built with JIB (no Dockerfiles). Python services use multi-stage Dockerfiles.

## Datadog instrumentation

### APM — Single-Step Instrumentation via Admission Controller

APM is instrumented with zero Dockerfile changes. The Datadog Cluster Agent's Admission Controller injects the tracer library automatically at pod startup based on pod template annotations.

**Deployed resources:** `kubernetes-manifests/datadog-agent.yaml`
- Datadog Cluster Agent (Deployment) — `gcr.io/datadoghq/cluster-agent:7.77.3` — runs the Admission Controller
- Datadog Node Agent (DaemonSet) — `gcr.io/datadoghq/agent:7.77.3` — collects metrics, traces, logs
- RBAC (ServiceAccounts, ClusterRoles, ClusterRoleBindings) for both
- Services: `datadog-cluster-agent` (port 5005) and `datadog-admission-controller` (port 443→8000)

**Library versions pinned in pod template annotations (per service manifest):**
- Python services: `admission.datadoghq.com/python-lib.version: "v3"` (dd-trace-py v3)
- Java services: `admission.datadoghq.com/java-lib.version: "v1.60.3"`

**Unified Service Tagging** is applied via pod template labels on all services:
```yaml
tags.datadoghq.com/env: "development"
tags.datadoghq.com/service: "<service-name>"
tags.datadoghq.com/version: "v0.6.9"
```

`DD_SERVICE`, `DD_ENV`, `DD_VERSION`, `DD_AGENT_HOST`, and `DD_LOGS_INJECTION` are also set as explicit env vars on every service container so the tracers pick them up.

### RUM — Browser SDK

RUM is injected into `src/frontend/templates/shared/html_head.html` (the shared `<head>` included on every page). It renders conditionally — only when `DD_RUM_APPLICATION_ID` and `DD_RUM_CLIENT_TOKEN` are both non-empty. SDK version: v6 (CDN).

To enable RUM, edit the `datadog-config` ConfigMap:
```sh
kubectl edit configmap datadog-config
# set DD_RUM_APPLICATION_ID and DD_RUM_CLIENT_TOKEN
# values come from app.datadoghq.com > UX Monitoring > RUM Applications
```

`frontend.py` reads these env vars and exposes them as global Jinja2 template variables (`dd_rum_application_id`, `dd_rum_client_token`, `dd_env`, `version`).

### Shared config

`kubernetes-manifests/config.yaml` contains the `datadog-config` ConfigMap:
- `DD_SITE` — `datadoghq.com`
- `DD_ENV` — `development`
- `DD_RUM_APPLICATION_ID` — set to enable RUM
- `DD_RUM_CLIENT_TOKEN` — set to enable RUM

## Secrets required in the cluster

```sh
# Datadog API key (already set via $DD_API_KEY env var)
kubectl create secret generic datadog-secret \
  --from-literal=api-key=$DD_API_KEY

# Shared auth token between Cluster Agent and Node Agents (32+ chars)
kubectl create secret generic datadog-cluster-agent-token \
  --from-literal=token=$(python3 -c "import secrets; print(secrets.token_hex(32))")
```

## Applying changes

```sh
kubectl apply -f kubernetes-manifests/datadog-agent.yaml
kubectl apply -f kubernetes-manifests/config.yaml
kubectl apply -f kubernetes-manifests/   # applies all service manifests
```

If the Datadog Agent DaemonSet or Cluster Agent Deployment already exist with a different `spec.selector` (e.g. from a prior Helm/Operator install), you must delete and recreate them — `spec.selector` is immutable:

```sh
# If previously managed by the Datadog Operator:
kubectl delete datadogagent datadog -n default
helm uninstall datadog-operator -n default

# Then re-apply
kubectl apply -f kubernetes-manifests/datadog-agent.yaml
```

## Local dev

The app requires a GKE cluster — it is not designed to run fully locally. For development, use Skaffold:

```sh
skaffold dev --profile development \
  --default-repo=us-central1-docker.pkg.dev/${PROJECT_ID}/bank-of-anthos \
  --module frontend   # rebuild only the frontend module
```

See `docs/development.md` for full prerequisites (Docker, kubectl, skaffold 2.9+, JDK 21+, Maven 3.9+, Python 3.12+).
