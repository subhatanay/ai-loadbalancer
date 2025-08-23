#!/bin/bash

# Quick service rebuild and redeploy script for Kubernetes
# Usage: ./redeploy-service.sh <service-name>
# Example: ./redeploy-service.sh inventory-service
# Special: ./redeploy-service.sh load-balancer (builds both load-balancer and rl-agent)

set -e

SERVICE_NAME=$1

if [ -z "$SERVICE_NAME" ]; then
    echo "❌ Error: Please provide service name"
    echo "Usage: $0 <service-name>"
    echo "Available services: user-service, cart-service, order-service, inventory-service, payment-service, notification-service, load-balancer"
    echo "Note: 'load-balancer' will build and deploy both load-balancer and rl-agent"
    exit 1
fi

echo "🚀 Starting rebuild and redeploy for: $SERVICE_NAME"

# Special handling for load-balancer (includes rl-agent)
if [ "$SERVICE_NAME" == "load-balancer" ]; then
    echo "🔗 Special mode: Building both load-balancer and rl-agent together"
    
    # Build Load Balancer
    echo "📦 Step 1a: Building load-balancer..."
    if [ ! -d "load-balancer" ]; then
        echo "❌ Error: load-balancer directory not found"
        exit 1
    fi
    
    cd load-balancer
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "❌ Load balancer Maven build failed"
        exit 1
    fi
    cd ..
    
    # Build RL Agent
    echo "📦 Step 1b: Building rl-agent..."
    if [ ! -d "rl_agent" ]; then
        echo "❌ Error: rl_agent directory not found"
        exit 1
    fi
    
    # Check if Dockerfile exists for rl-agent
    if [ ! -f "rl_agent/Dockerfile" ]; then
        echo "❌ Error: rl_agent/Dockerfile not found"
        exit 1
    fi
    
    # Build Docker images
    echo "🐳 Step 2a: Building load-balancer Docker image..."
    docker build -t "load-balancer:latest" "load-balancer/"
    if [ $? -ne 0 ]; then
        echo "❌ Load balancer Docker build failed"
        exit 1
    fi
    
    echo "🐳 Step 2b: Building rl-agent Docker image..."
    docker build -t "subhajgh/ai-loadbalancer-rl-agent:latest" "rl_agent/"
    if [ $? -ne 0 ]; then
        echo "❌ RL agent Docker build failed"
        exit 1
    fi
    
    # Load images into kind cluster
    echo "📥 Step 3a: Loading load-balancer image into kind cluster..."
    kind load docker-image "load-balancer:latest" --name ai-loadbalancer-cluster
    if [ $? -ne 0 ]; then
        echo "❌ Failed to load load-balancer image into kind cluster"
        exit 1
    fi
    
    echo "📥 Step 3b: Loading rl-agent image into kind cluster..."
    kind load docker-image "subhajgh/ai-loadbalancer-rl-agent:latest" --name ai-loadbalancer-cluster
    if [ $? -ne 0 ]; then
        echo "❌ Failed to load rl-agent image into kind cluster"
        exit 1
    fi
    
    # Restart ai-loadbalancer deployment (contains both containers)
    echo "♻️ Step 4: Restarting ai-loadbalancer deployment (both containers)..."
    kubectl rollout restart deployment ai-loadbalancer -n ai-loadbalancer
    if [ $? -ne 0 ]; then
        echo "❌ Failed to restart ai-loadbalancer deployment"
        exit 1
    fi
    
    # Wait for rollout to complete
    echo "⏳ Step 5: Waiting for rollout to complete..."
    kubectl rollout status deployment ai-loadbalancer -n ai-loadbalancer --timeout=300s
    if [ $? -ne 0 ]; then
        echo "❌ Rollout failed or timed out for ai-loadbalancer"
        exit 1
    fi
    
    echo ""
    echo "🎉 SUCCESS: Both load-balancer and rl-agent have been rebuilt and redeployed!"
    echo "📊 AI Load Balancer deployment status:"
    kubectl get deployment ai-loadbalancer -n ai-loadbalancer
    echo ""
    echo "📊 Pod status:"
    kubectl get pods -l app=ai-loadbalancer -n ai-loadbalancer
    echo ""
    echo "📝 Recent ai-loadbalancer logs:"
    kubectl logs -l app=ai-loadbalancer -c ai-loadbalancer -n ai-loadbalancer --tail=5
    echo ""
    echo "📝 Recent rl-agent logs:"
    kubectl logs -l app=ai-loadbalancer -c rl-agent -n ai-loadbalancer --tail=5
    
    exit 0
fi

# Regular service handling
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

