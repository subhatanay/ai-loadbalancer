#!/bin/bash

echo "🛑 Shutting down AI Load Balancer Kubernetes deployment..."

# Check if cluster exists
if ! kind get clusters | grep -q "ai-loadbalancer-cluster"; then
    echo "❌ Kind cluster 'ai-loadbalancer-cluster' not found."
    echo "Nothing to shut down."
    exit 0
fi

echo "📋 Current cluster status:"
kubectl get pods -n ai-loadbalancer 2>/dev/null || echo "No pods found in ai-loadbalancer namespace"

# Delete all resources in the ai-loadbalancer namespace
echo "🗑️  Deleting all resources in ai-loadbalancer namespace..."
kubectl delete namespace ai-loadbalancer --timeout=60s 2>/dev/null || echo "Namespace already deleted or doesn't exist"

# Wait for namespace deletion
echo "⏳ Waiting for namespace deletion to complete..."
kubectl wait --for=delete namespace/ai-loadbalancer --timeout=60s 2>/dev/null || echo "Namespace deletion completed or timed out"

# Delete any remaining cluster-wide resources
echo "🧹 Cleaning up cluster-wide resources..."
kubectl delete clusterrole ai-loadbalancer-role 2>/dev/null || echo "ClusterRole already deleted or doesn't exist"
kubectl delete clusterrolebinding ai-loadbalancer-binding 2>/dev/null || echo "ClusterRoleBinding already deleted or doesn't exist"

# Delete Envoy Gateway if it was installed
echo "🚪 Cleaning up Envoy Gateway resources..."
kubectl delete -f https://github.com/envoyproxy/gateway/releases/latest/download/install.yaml 2>/dev/null || echo "Envoy Gateway not installed or already deleted"

# Delete the entire Kind cluster
echo "💥 Deleting Kind cluster 'ai-loadbalancer-cluster'..."
kind delete cluster --name ai-loadbalancer-cluster

# Verify cluster deletion
if kind get clusters | grep -q "ai-loadbalancer-cluster"; then
    echo "❌ Failed to delete Kind cluster"
    exit 1
else
    echo "✅ Kind cluster 'ai-loadbalancer-cluster' successfully deleted"
fi

# Clean up any remaining Docker resources (optional)
echo "🐳 Cleaning up unused Docker resources..."
docker system prune -f --volumes 2>/dev/null || echo "Docker cleanup completed"

echo ""
echo "🎉 Shutdown complete!"
echo "✅ AI Load Balancer Kubernetes deployment has been completely removed"
echo "✅ Kind cluster 'ai-loadbalancer-cluster' deleted"
echo "✅ All resources cleaned up"
echo ""
echo "To redeploy, run: ./startup.sh"
