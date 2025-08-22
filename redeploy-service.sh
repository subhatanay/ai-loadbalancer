#!/bin/bash

# Quick service rebuild and redeploy script for Kubernetes
# Usage: ./redeploy-service.sh <service-name>
# Example: ./redeploy-service.sh inventory-service

set -e

SERVICE_NAME=$1

if [ -z "$SERVICE_NAME" ]; then
    echo "❌ Error: Please provide service name"
    echo "Usage: $0 <service-name>"
    echo "Available services: user-service, cart-service, order-service, inventory-service, payment-service, notification-service, ai-loadbalancer"
    exit 1
fi

echo "🚀 Starting rebuild and redeploy for: $SERVICE_NAME"

# Check if service directory exists
if [ ! -d "$SERVICE_NAME" ]; then
    echo "❌ Error: Service directory '$SERVICE_NAME' not found"
    exit 1
fi

# Step 1: Build the service
echo "📦 Step 1: Building $SERVICE_NAME..."
cd "$SERVICE_NAME"
mvn clean package -DskipTests
if [ $? -ne 0 ]; then
    echo "❌ Maven build failed"
    exit 1
fi
cd ..

# Step 2: Build Docker image
echo "🐳 Step 2: Building Docker image..."
docker build -t "$SERVICE_NAME:latest" "$SERVICE_NAME/"
if [ $? -ne 0 ]; then
    echo "❌ Docker build failed"
    exit 1
fi

# Step 3: Load image into kind cluster
echo "📥 Step 3: Loading image into kind cluster..."
kind load docker-image "$SERVICE_NAME:latest" --name ai-loadbalancer-cluster
if [ $? -ne 0 ]; then
    echo "❌ Failed to load image into kind cluster"
    exit 1
fi

# Set deployment name, defaulting to service name
DEPLOYMENT_NAME=$SERVICE_NAME
if [ "$SERVICE_NAME" == "load-balancer" ]; then
    DEPLOYMENT_NAME="ai-loadbalancer"
    echo "ℹ️  Service 'load-balancer' maps to deployment 'ai-loadbalancer'"
fi

# Step 4: Restart deployment
echo "♻️ Step 4: Restarting Kubernetes deployment..."
kubectl rollout restart deployment "$DEPLOYMENT_NAME" -n ai-loadbalancer
if [ $? -ne 0 ]; then
    echo "❌ Failed to restart deployment for $DEPLOYMENT_NAME"
    exit 1
fi

# Step 5: Wait for rollout to complete
echo "⏳ Step 5: Waiting for rollout to complete..."
kubectl rollout status deployment "$DEPLOYMENT_NAME" -n ai-loadbalancer --timeout=300s
if [ $? -ne 0 ]; then
    echo "❌ Rollout failed or timed out for $DEPLOYMENT_NAME"
    exit 1
fi

# If the updated service is not the load balancer itself, restart the load balancer to pick up changes
if [ "$DEPLOYMENT_NAME" != "ai-loadbalancer" ]; then
    echo "🔄 Step 6: Restarting AI Load Balancer to apply changes..."
    kubectl rollout restart deployment ai-loadbalancer -n ai-loadbalancer
    if [ $? -ne 0 ]; then
        echo "❌ Failed to restart ai-loadbalancer deployment"
        exit 1
    fi

    echo "⏳ Step 7: Waiting for AI Load Balancer rollout to complete..."
    kubectl rollout status deployment ai-loadbalancer -n ai-loadbalancer --timeout=120s
    if [ $? -ne 0 ]; then
        echo "❌ AI Load Balancer rollout failed or timed out"
        exit 1
    fi
fi

echo ""
echo "🎉 SUCCESS: $DEPLOYMENT_NAME has been rebuilt and redeployed!"
echo "📊 Service status for $DEPLOYMENT_NAME:"
kubectl get deployment "$DEPLOYMENT_NAME" -n ai-loadbalancer
echo ""
echo "📊 AI Load Balancer status:"
kubectl get deployment ai-loadbalancer -n ai-loadbalancer
echo ""
echo "📝 Recent $DEPLOYMENT_NAME logs:"
kubectl logs -l app="$DEPLOYMENT_NAME" -n ai-loadbalancer --tail=5

