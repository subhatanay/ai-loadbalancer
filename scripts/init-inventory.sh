#!/bin/bash

# =====================================================
# Inventory Data Initialization Script
# =====================================================
# This script initializes the inventory database with sample data

set -e

echo "🚀 Starting inventory data initialization..."

# Check if we're in Kubernetes environment
if kubectl get pods -n ai-loadbalancer >/dev/null 2>&1; then
    echo "📦 Kubernetes environment detected"
    
    # Find PostgreSQL pod
    POSTGRES_POD=$(kubectl get pods -l app=postgres -n ai-loadbalancer -o jsonpath='{.items[0].metadata.name}')
    
    if [ -z "$POSTGRES_POD" ]; then
        echo "❌ Error: PostgreSQL pod not found"
        exit 1
    fi
    
    echo "📊 Found PostgreSQL pod: $POSTGRES_POD"
    
    # Copy SQL script to pod
    echo "📋 Copying SQL script to PostgreSQL pod..."
    kubectl cp ./init-inventory-data.sql ai-loadbalancer/$POSTGRES_POD:/tmp/init-inventory-data.sql
    
    # Execute SQL script
    echo "⚡ Executing inventory data initialization..."
    kubectl exec -n ai-loadbalancer $POSTGRES_POD -- psql -U postgres -f /tmp/init-inventory-data.sql
    
    echo "🧹 Cleaning up temporary files..."
    kubectl exec -n ai-loadbalancer $POSTGRES_POD -- rm -f /tmp/init-inventory-data.sql
    
else
    echo "🐳 Local Docker environment detected"
    
    # Check if PostgreSQL container is running
    if docker ps --format "table {{.Names}}" | grep -q postgres; then
        POSTGRES_CONTAINER=$(docker ps --filter "name=postgres" --format "{{.Names}}" | head -n1)
        echo "📊 Found PostgreSQL container: $POSTGRES_CONTAINER"
        
        # Copy and execute SQL script
        echo "📋 Copying SQL script to PostgreSQL container..."
        docker cp ./init-inventory-data.sql $POSTGRES_CONTAINER:/tmp/init-inventory-data.sql
        
        echo "⚡ Executing inventory data initialization..."
        docker exec $POSTGRES_CONTAINER psql -U postgres -f /tmp/init-inventory-data.sql
        
        echo "🧹 Cleaning up temporary files..."
        docker exec $POSTGRES_CONTAINER rm -f /tmp/init-inventory-data.sql
    else
        echo "❌ Error: PostgreSQL container not found"
        echo "Please ensure PostgreSQL is running in Docker"
        exit 1
    fi
fi

echo ""
echo "✅ Inventory data initialization completed successfully!"
echo ""
echo "📊 Summary:"
echo "   • Products: 20+ items across multiple categories"
echo "   • Warehouses: US East, US West, EU Central, Asia Pacific"
echo "   • Multi-warehouse products: LAPTOP-001, PHONE-001, SHIRT-001, etc."
echo "   • Single-warehouse products: HEADPHONES-001, BOOK-001, etc."
echo ""
echo "🔍 You can now test the inventory service with:"
echo "   curl -X GET \"http://localhost:8084/api/inventory/product/LAPTOP-001\""
echo "   curl -X GET \"http://localhost:8084/api/inventory/all\""
echo ""
