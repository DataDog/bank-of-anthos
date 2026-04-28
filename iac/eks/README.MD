Setup step in AWS EKS:

# 1. SSO login
aws sso login 

# 2. Create EKS cluster (~15 min)
eksctl create cluster -f iac/eks/cluster.yaml

# 3. Configure kubectl
aws eks update-kubeconfig --name bank-of-anthos --region us-west-2 --profile <profile>
kubectl get nodes

# 4. Set up tagged StorageClass
kubectl patch storageclass gp2 -p '{"metadata":{"annotations":{"storageclass.kubernetes.io/is-default-class":"false"}}}'
kubectl apply -f iac/eks/storageclass.yaml

# 5. Deploy Datadog
helm repo add datadog https://helm.datadoghq.com && helm repo update
helm install datadog-operator datadog/datadog-operator --namespace datadog --create-namespace
kubectl create secret generic datadog-secret --from-literal api-key=<YOUR_DD_API_KEY> -n datadog
kubectl apply -f datadog-agent/datadog-agent-eks.yaml

# 6. Deploy Bank of Anthos
openssl genrsa -out /tmp/jwtRS256.key 4096
openssl rsa -in /tmp/jwtRS256.key -pubout -out /tmp/jwtRS256.key.pub
kubectl create secret generic jwt-key \
  --from-file=jwtRS256.key=/tmp/jwtRS256.key \
  --from-file=jwtRS256.key.pub=/tmp/jwtRS256.key.pub
rm /tmp/jwtRS256.key /tmp/jwtRS256.key.pub
kubectl apply -f kubernetes-manifests/
kubectl get pods -w


# 7. Get the frontend URL
kubectl get service frontend
