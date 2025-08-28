#!/bin/bash

# Grafana Dashboard Auto-Provisioning Script
# This script automatically imports all dashboards into Grafana

set -e

# Configuration
GRAFANA_URL="http://localhost:3000"
GRAFANA_USER="admin"
GRAFANA_PASSWORD="admin"
DASHBOARD_DIR="../grafana/dashboards_v2"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 Starting Grafana Dashboard Auto-Provisioning${NC}"

# Function to wait for Grafana to be ready
wait_for_grafana() {
    echo -e "${YELLOW}⏳ Waiting for Grafana to be ready...${NC}"
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "${GRAFANA_URL}/api/health" > /dev/null 2>&1; then
            echo -e "${GREEN}✅ Grafana is ready!${NC}"
            return 0
        fi
        echo -e "${YELLOW}   Attempt ${attempt}/${max_attempts} - Grafana not ready yet...${NC}"
        sleep 5
        ((attempt++))
    done
    
    echo -e "${RED}❌ Grafana failed to start within expected time${NC}"
    return 1
}

# Function to create or update dashboard
import_dashboard() {
    local dashboard_file="$1"
    local dashboard_name=$(basename "$dashboard_file" .json)
    
    echo -e "${BLUE}📊 Importing dashboard: ${dashboard_name}${NC}"
    
    # Read dashboard JSON and wrap it in the import format
    local dashboard_json=$(cat "$dashboard_file")
    local import_payload=$(cat <<EOF
{
  "dashboard": $dashboard_json,
  "overwrite": true,
  "inputs": [],
  "folderId": 0
}
EOF
)
    
    # Import dashboard via Grafana API
    local response=$(curl -s -w "%{http_code}" -o /tmp/grafana_response.json \
        -X POST \
        -H "Content-Type: application/json" \
        -u "${GRAFANA_USER}:${GRAFANA_PASSWORD}" \
        -d "$import_payload" \
        "${GRAFANA_URL}/api/dashboards/import")
    
    local http_code="${response: -3}"
    
    if [ "$http_code" = "200" ]; then
        echo -e "${GREEN}✅ Successfully imported: ${dashboard_name}${NC}"
        return 0
    else
        echo -e "${RED}❌ Failed to import ${dashboard_name} (HTTP: ${http_code})${NC}"
        echo -e "${RED}   Response: $(cat /tmp/grafana_response.json)${NC}"
        return 1
    fi
}

# Function to create datasource if it doesn't exist
setup_prometheus_datasource() {
    echo -e "${BLUE}🔗 Setting up Prometheus datasource...${NC}"
    
    # Check if datasource already exists
    local existing_ds=$(curl -s -u "${GRAFANA_USER}:${GRAFANA_PASSWORD}" \
        "${GRAFANA_URL}/api/datasources/name/prometheus" 2>/dev/null || echo "")
    
    if [ -n "$existing_ds" ] && [ "$existing_ds" != "Not found" ]; then
        echo -e "${GREEN}✅ Prometheus datasource already exists${NC}"
        return 0
    fi
    
    # Create Prometheus datasource
    local datasource_payload=$(cat <<EOF
{
  "name": "prometheus",
  "type": "prometheus",
  "url": "http://prometheus:9090",
  "access": "proxy",
  "isDefault": true,
  "basicAuth": false
}
EOF
)
    
    local response=$(curl -s -w "%{http_code}" -o /tmp/datasource_response.json \
        -X POST \
        -H "Content-Type: application/json" \
        -u "${GRAFANA_USER}:${GRAFANA_PASSWORD}" \
        -d "$datasource_payload" \
        "${GRAFANA_URL}/api/datasources")
    
    local http_code="${response: -3}"
    
    if [ "$http_code" = "200" ]; then
        echo -e "${GREEN}✅ Successfully created Prometheus datasource${NC}"
    else
        echo -e "${RED}❌ Failed to create Prometheus datasource (HTTP: ${http_code})${NC}"
        echo -e "${RED}   Response: $(cat /tmp/datasource_response.json)${NC}"
    fi
}

# Main execution
main() {
    echo -e "${BLUE}🔧 Grafana Dashboard Auto-Provisioning Script${NC}"
    echo -e "${BLUE}================================================${NC}"
    
    # Wait for Grafana to be ready
    if ! wait_for_grafana; then
        exit 1
    fi
    
    # Setup Prometheus datasource
    setup_prometheus_datasource
    
    # Check if dashboard directory exists
    if [ ! -d "$DASHBOARD_DIR" ]; then
        echo -e "${RED}❌ Dashboard directory not found: $DASHBOARD_DIR${NC}"
        exit 1
    fi
    
    # Import all dashboard files
    local success_count=0
    local total_count=0
    
    echo -e "${BLUE}📁 Scanning dashboard directory: $DASHBOARD_DIR${NC}"
    
    for dashboard_file in "$DASHBOARD_DIR"/*.json; do
        if [ -f "$dashboard_file" ]; then
            ((total_count++))
            if import_dashboard "$dashboard_file"; then
                ((success_count++))
            fi
        fi
    done
    
    # Summary
    echo -e "${BLUE}================================================${NC}"
    echo -e "${GREEN}✅ Dashboard import completed!${NC}"
    echo -e "${GREEN}   Successfully imported: ${success_count}/${total_count} dashboards${NC}"
    
    if [ $success_count -eq $total_count ]; then
        echo -e "${GREEN}🎉 All dashboards imported successfully!${NC}"
        echo -e "${BLUE}🌐 Access Grafana at: ${GRAFANA_URL}${NC}"
        exit 0
    else
        echo -e "${YELLOW}⚠️  Some dashboards failed to import${NC}"
        exit 1
    fi
}

# Cleanup function
cleanup() {
    rm -f /tmp/grafana_response.json /tmp/datasource_response.json
}

# Set trap for cleanup
trap cleanup EXIT

# Run main function
main "$@"
