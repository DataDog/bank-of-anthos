To instrument Datadog run the below:
helm repo add datadog https://helm.datadoghq.com
helm install datadog-operator datadog/datadog-operator --namespace datadog --create-namespace
kubectl create secret generic datadog-secret --from-literal api-key=<API_KEY> -n datadog --from-literal app-key=<APP_KEY>

kubectl apply -f datadog-agent/datadog-agent.yaml

Check status: kubectl get pods -n datadog