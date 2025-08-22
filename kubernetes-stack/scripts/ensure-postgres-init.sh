#!/bin/bash

# Script to ensure PostgreSQL initialization runs properly on fresh deployments
# This addresses the issue where Kind persistent volumes prevent init scripts from running

NAMESPACE="ai-loadbalancer"

echo "🔧 Ensuring PostgreSQL initialization..."

# Function to check if ecommerce_user exists
check_user_exists() {
    kubectl exec -n $NAMESPACE deployment/postgres -- psql -U postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='ecommerce_user';" 2>/dev/null
}

# Function to run initialization script
run_init_script() {
    echo "📝 Running PostgreSQL initialization script..."
    kubectl exec -n $NAMESPACE deployment/postgres -- psql -U postgres -f /docker-entrypoint-initdb.d/init-databases.sql
}

# Wait for PostgreSQL to be ready
echo "⏳ Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=ready pod -l app=postgres -n $NAMESPACE --timeout=120s

if [ $? -ne 0 ]; then
    echo "❌ PostgreSQL failed to become ready"
    exit 1
fi

# Check if initialization is needed
echo "🔍 Checking if database initialization is needed..."
USER_EXISTS=$(check_user_exists)

if [ "$USER_EXISTS" != "1" ]; then
    echo "🚀 ecommerce_user not found. Running initialization..."
    run_init_script
    
    # Verify initialization was successful
    sleep 5
    USER_EXISTS_AFTER=$(check_user_exists)
    
    if [ "$USER_EXISTS_AFTER" = "1" ]; then
        echo "✅ PostgreSQL initialization completed successfully!"
    else
        echo "❌ PostgreSQL initialization failed!"
        exit 1
    fi
else
    echo "✅ PostgreSQL already initialized (ecommerce_user exists)"
fi

echo "🎉 PostgreSQL is ready for microservices!"
