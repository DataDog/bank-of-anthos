# Bank of Anthos — Claude Context

## What this repo is

Bank of Anthos is a sample HTTP-based web banking app used as a Datadog APM/RUM instrumentation demo. It runs on Kubernetes (Docker Desktop locally, GKE in production). The app is already deployed and accessible via the LoadBalancer IP of the `frontend` service:

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

Instrumentation is managed by the **Datadog Operator** using a `DatadogAgent` custom resource. No changes to application Dockerfiles or `kubernetes-manifests/` service manifests are required.

### Deployed resources

**File:** `datadog-agent/datadog-agent.yaml` — `DatadogAgent` CR (API: `datadoghq.com/v2alpha1`)
**Namespace:** `datadog`

### Deploying the Datadog Operator and agent

```sh
# 1. Install the Datadog Operator via Helm
helm repo add datadog https://helm.datadoghq.com
helm install datadog-operator datadog/datadog-operator \
  --namespace datadog --create-namespace

# 2. Create the API key secret (app-key is not required)
kubectl create secret generic datadog-secret \
  --from-literal api-key=<DD_API_KEY> \
  -n datadog

# 3. Apply the DatadogAgent CR
kubectl apply -f datadog-agent/datadog-agent.yaml

# Check status
kubectl get pods -n datadog
kubectl get datadogagent -n datadog
```

### APM — Single-Step Instrumentation

APM is enabled via `spec.features.apm.instrumentation` in the DatadogAgent CR — the Cluster Agent's Admission Controller injects the tracer at pod creation time with zero Dockerfile changes.

**Tracer versions (pinned in `datadog-agent/datadog-agent.yaml`):**
- Java: `v1`
- Python: `v4`
- .NET: `v3`

**Additional trace configs injected into all pods:**
- `DD_PROFILING_ENABLED: auto`
- `DD_DATA_STREAMS_ENABLED: true`

### RUM — Browser SDK

RUM is configured directly in the DatadogAgent CR under `spec.features.apm.instrumentation.targets[].ddTraceConfigs`. The following env vars are injected into all instrumented pods:

- `DD_RUM_ENABLED: true`
- `DD_RUM_APPLICATION_ID: 8bc3f97c-a1fa-430e-82c7-171c0835f30c`
- `DD_RUM_CLIENT_TOKEN: pub4b41d998d78af38daebbb62d178815fb`
- `DD_RUM_REMOTE_CONFIGURATION_ID: 40040aad-de74-4384-aff1-5b8614f865e1`
- `DD_RUM_SITE: datadoghq.com`

To update RUM credentials, edit `datadog-agent/datadog-agent.yaml` and re-apply.

### Other features enabled

| Feature | Config key |
|---|---|
| Log collection (all containers) | `logCollection.enabled` |
| App Security (threats, SCA, IAST) | `asm.*` |
| Cloud Workload Security | `cws.enabled` |
| Cloud Security Posture Mgmt | `cspm.enabled` |
| SBOM (container images + host) | `sbom.*` |
| Universal Service Monitoring | `usm.enabled` |
| Network Performance Monitoring | `npm.enabled` |
| Live Process Collection | `liveProcessCollection.enabled` |
| Workload Autoscaling | `autoscaling.workload.enabled` |
| AppSec injector | annotation `agent.datadoghq.com/appsec.injector.enabled: "true"` |

## Cluster name

The `spec.global.clusterName` in `datadog-agent/datadog-agent.yaml` is currently set to `"your-cluster-name-here"` — update this to the actual cluster name before deploying to production.

## Checking agent status

```sh
kubectl get pods -n datadog          # all Datadog pods
kubectl get datadogagent -n datadog  # operator-managed CR status

# Logs
kubectl logs -n datadog -l app.kubernetes.io/component=agent -c agent --tail=50
kubectl logs -n datadog -l app.kubernetes.io/component=cluster-agent --tail=50
```

## Local dev

The app requires a Kubernetes cluster. For local iteration, Docker Desktop Kubernetes works. For production, use GKE and Skaffold:

```sh
skaffold dev --profile development \
  --default-repo=us-central1-docker.pkg.dev/${PROJECT_ID}/bank-of-anthos \
  --module frontend   # rebuild only the frontend module
```

See `docs/development.md` for full prerequisites (Docker, kubectl, skaffold 2.9+, JDK 21+, Maven 3.9+, Python 3.12+).
