#!/bin/bash

# Comprehensive Service Log Collection Script
# Collects logs from all services for failure analysis

LOG_DIR="service_logs"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
NAMESPACE="ai-loadbalancer"

echo "🔍 AI Load Balancer - Service Log Collection"
echo "=============================================="
echo "Timestamp: $TIMESTAMP"
echo "Namespace: $NAMESPACE"
echo "Log Directory: $LOG_DIR"
echo ""

# Create log directory
mkdir -p "$LOG_DIR"

# Function to collect logs for a service
collect_service_logs() {
    local service_name=$1
    local log_file="$LOG_DIR/${service_name}_${TIMESTAMP}.log"
    
    echo "📋 Collecting logs for $service_name..."
    
    # Get pod names for the service
    pods=$(kubectl get pods -n $NAMESPACE -l app=$service_name -o jsonpath='{.items[*].metadata.name}')
    
    if [ -z "$pods" ]; then
        echo "❌ No pods found for service: $service_name"
        echo "No pods found for $service_name at $TIMESTAMP" > "$log_file"
        return
    fi
    
    echo "🔍 Found pods for $service_name: $pods"
    
    # Collect logs from all pods of this service
    {
        echo "=========================================="
        echo "SERVICE: $service_name"
        echo "TIMESTAMP: $TIMESTAMP"
        echo "PODS: $pods"
        echo "=========================================="
        echo ""
        
        for pod in $pods; do
            echo "===========================================" 
            echo "POD: $pod"
            echo "==========================================="
            
            # Get recent logs (last 500 lines)
            echo "--- RECENT LOGS (last 500 lines) ---"
            kubectl logs -n $NAMESPACE $pod 
            
            echo ""
            echo "--- PREVIOUS LOGS (if container restarted) ---"
            kubectl logs -n $NAMESPACE $pod --previous --tail=200 2>/dev/null || echo "No previous logs available"
            
            echo ""
            echo "--- POD DESCRIPTION ---"
            kubectl describe pod -n $NAMESPACE $pod
            
            echo ""
            echo "=========================================="
            echo ""
        done
    } > "$log_file" 2>&1
    
    echo "✅ Logs saved to: $log_file"
}

# Function to collect service status
collect_service_status() {
    local status_file="$LOG_DIR/service_status_${TIMESTAMP}.log"
    
    echo "📊 Collecting overall service status..."
    
    {
        echo "=========================================="
        echo "KUBERNETES SERVICE STATUS"
        echo "TIMESTAMP: $TIMESTAMP"
        echo "NAMESPACE: $NAMESPACE"
        echo "=========================================="
        echo ""
        
        echo "--- ALL PODS STATUS ---"
        kubectl get pods -n $NAMESPACE -o wide
        
        echo ""
        echo "--- ALL SERVICES ---"
        kubectl get services -n $NAMESPACE
        
        echo ""
        echo "--- DEPLOYMENTS STATUS ---"
        kubectl get deployments -n $NAMESPACE
        
        echo ""
        echo "--- RECENT EVENTS ---"
        kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' | tail -50
        
        echo ""
        echo "--- RESOURCE USAGE ---"
        kubectl top pods -n $NAMESPACE 2>/dev/null || echo "Metrics server not available"
        
    } > "$status_file" 2>&1
    
    echo "✅ Service status saved to: $status_file"
}

# Main execution
echo "🚀 Starting log collection for all services..."
echo ""

# Collect status first
collect_service_status

# Collect logs for each microservice
services=(
    "ai-loadbalancer"
    "user-service" 
    "cart-service"
    "order-service"
    "inventory-service"
    "payment-service"
    "notification-service"
    "rl-experience-collector"
)

echo "📋 Services to collect logs from:"
for service in "${services[@]}"; do
    echo "  - $service"
done
echo ""

# Collect logs for each service
for service in "${services[@]}"; do
    collect_service_logs "$service"
    echo ""
done

# Collect infrastructure logs
echo "🏗️ Collecting infrastructure service logs..."
infra_services=("postgres" "redis" "kafka" "prometheus" "grafana")

for service in "${infra_services[@]}"; do
    collect_service_logs "$service"
done

echo ""
echo "🎯 LOG COLLECTION SUMMARY"
echo "========================="
echo "📁 Log directory: $LOG_DIR"
echo "📋 Files created:"
ls -la "$LOG_DIR"/*"$TIMESTAMP"* 2>/dev/null || echo "No log files found"

echo ""
echo "🔍 QUICK ANALYSIS COMMANDS:"
echo "# Check for errors across all services:"
echo "grep -i error $LOG_DIR/*$TIMESTAMP*.log"
echo ""
echo "# Check for specific service issues:"
echo "grep -i 'exception\\|error\\|fail' $LOG_DIR/order-service_$TIMESTAMP.log"
echo "grep -i 'exception\\|error\\|fail' $LOG_DIR/cart-service_$TIMESTAMP.log"
echo "grep -i 'exception\\|error\\|fail' $LOG_DIR/inventory-service_$TIMESTAMP.log"
echo ""
echo "# Check authentication issues:"
echo "grep -i 'auth\\|jwt\\|token' $LOG_DIR/user-service_$TIMESTAMP.log"
echo ""
echo "✅ Log collection completed! Analyze the logs to identify failure root causes."
