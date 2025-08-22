#!/bin/bash

# Create Kind cluster
echo "Creating Kind cluster..."
kind create cluster --name ai-loadbalancer-cluster

# Load Docker images into Kind cluster
echo "Loading Docker images into Kind cluster..."
kind load docker-image user-service:latest --name ai-loadbalancer-cluster
kind load docker-image cart-service:latest --name ai-loadbalancer-cluster
kind load docker-image order-service:latest --name ai-loadbalancer-cluster
kind load docker-image inventory-service:latest --name ai-loadbalancer-cluster
kind load docker-image payment-service:latest --name ai-loadbalancer-cluster
kind load docker-image notification-service:latest --name ai-loadbalancer-cluster
kind load docker-image load-balancer:latest --name ai-loadbalancer-cluster
kind load docker-image rl-experience-collector:latest --name ai-loadbalancer-cluster
kind load docker-image subhajgh/ai-loadbalancer-rl-agent --name ai-loadbalancer-cluster

# Apply Kubernetes manifests
echo "Applying Kubernetes manifests..."
kubectl apply -f config-yaml/namespace.yaml
kubectl apply -f config-yaml/postgres-init-configmap.yaml
kubectl apply -f config-yaml/postgresql.yaml
kubectl apply -f config-yaml/redis.yaml
kubectl apply -f config-yaml/zookeeper.yaml
kubectl apply -f config-yaml/kafka.yaml
kubectl apply -f config-yaml/mailhog.yaml
kubectl apply -f config-yaml/user-service.yaml
kubectl apply -f config-yaml/cart-service.yaml
kubectl apply -f config-yaml/order-service.yaml
kubectl apply -f config-yaml/inventory-service.yaml
kubectl apply -f config-yaml/payment-service.yaml
kubectl apply -f config-yaml/notification-service.yaml
kubectl apply -f config-yaml/cluster-role.yaml
kubectl apply -f config-yaml/prometheus-rbac.yaml
kubectl apply -f config-yaml/prometheus.yaml
kubectl apply -f config-yaml/grafana.yaml
kubectl apply -f config-yaml/rl-collector.yaml
kubectl apply -f config-yaml/ai-loadbalancer.yaml

# Wait for deployments to be ready
echo "Waiting for deployments to be ready..."
kubectl wait --for=condition=available --timeout=300s deployment/redis -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/postgres -n ai-loadbalancer

# Ensure PostgreSQL initialization
echo "Ensuring PostgreSQL database initialization..."
./scripts/ensure-postgres-init.sh

kubectl wait --for=condition=available --timeout=300s deployment/zookeeper -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/kafka -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/mailhog -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/user-service -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/cart-service -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/order-service -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/inventory-service -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/payment-service -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/notification-service -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/prometheus -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/grafana -n ai-loadbalancer
kubectl wait --for=condition=available --timeout=300s deployment/ai-loadbalancer -n ai-loadbalancer

echo "Deployment complete!"

echo "Deployment complete!"
echo "Services are now running in the ai-loadbalancer namespace."
echo ""
echo "To access services, use port-forwarding:"
echo "  AI Load Balancer: kubectl port-forward -n ai-loadbalancer service/ai-loadbalancer-service 8080:8080"
echo "  Prometheus:       kubectl port-forward -n ai-loadbalancer service/prometheus-service 9090:9090"
echo "  Grafana:          kubectl port-forward -n ai-loadbalancer service/grafana-service 3000:3000"
echo "  MailHog:          kubectl port-forward -n ai-loadbalancer service/mailhog 8025:8025"
echo ""
echo "Check pod status with: kubectl get pods -n ai-loadbalancer"

echo "Access URLs:"
echo "Load Balancer: http://localhost:8080"
echo "Prometheus: http://localhost:9090"
echo "Grafana: http://localhost:3000 (admin/admin)"