# Grafana Dashboard Auto-Provisioning

This directory contains scripts to automatically provision Grafana dashboards without manual import.

## Scripts Available

### 1. `setup-grafana-dashboards.sh`
**Bash script for dashboard provisioning**
- Waits for Grafana to be ready
- Creates Prometheus datasource automatically
- Imports all dashboards from `../grafana/dashboards_v2/`
- Provides colored output and error handling

**Usage:**
```bash
cd scripts
./setup-grafana-dashboards.sh
```

### 2. `update-grafana-dashboards.py`
**Python script for dashboard provisioning**
- More robust error handling
- Better API interaction
- JSON validation
- Detailed logging

**Usage:**
```bash
cd scripts
python3 update-grafana-dashboards.py
```

**Requirements:**
```bash
pip install requests
```

## Kubernetes Integration

### Automatic Dashboard Provisioning
The `startup.sh` script now includes automatic dashboard provisioning:

1. **Deploys Grafana with ConfigMaps** - Uses `grafana-dashboard-provisioning.yaml`
2. **Auto-imports dashboards** - Runs Python script after Grafana is ready
3. **Sets up datasources** - Automatically configures Prometheus connection

### Manual Dashboard Update
If you need to update dashboards after deployment:

```bash
# Port-forward Grafana
kubectl port-forward -n ai-loadbalancer service/grafana-service 3000:3000 &

# Run dashboard update
cd scripts
python3 update-grafana-dashboards.py

# Stop port-forward
pkill -f "kubectl port-forward.*grafana"
```

## Configuration

### Grafana Settings
- **URL**: `http://localhost:3000`
- **Username**: `admin`
- **Password**: `admin`

### Dashboard Location
- **Source**: `../grafana/dashboards_v2/*.json`
- **Target**: Grafana via API import

### Prometheus Datasource
- **URL**: `http://prometheus:9090`
- **Access**: Proxy mode
- **Default**: Yes

## Troubleshooting

### Dashboard Import Fails
1. Check Grafana is running: `kubectl get pods -n ai-loadbalancer`
2. Verify port-forward: `curl http://localhost:3000/api/health`
3. Check dashboard JSON syntax: `python -m json.tool dashboard.json`

### Datasource Issues
1. Verify Prometheus is running: `kubectl get svc -n ai-loadbalancer prometheus`
2. Check Prometheus metrics: `curl http://localhost:9090/metrics`

### Script Permissions
```bash
chmod +x setup-grafana-dashboards.sh
chmod +x update-grafana-dashboards.py
```

## Features

✅ **Automatic Grafana readiness check**
✅ **Prometheus datasource auto-creation**
✅ **Bulk dashboard import**
✅ **Error handling and retry logic**
✅ **Colored output for better UX**
✅ **Kubernetes integration**
✅ **Dashboard overwrite support**
✅ **JSON validation**

## Dashboard Structure

The scripts automatically import all dashboards including:
- **AI Load Balancer Comprehensive Dashboard**
- **Service-specific dashboards** (User, Cart, Order, etc.)
- **RL Agent monitoring dashboards**
- **System health dashboards**

All dashboards are organized with proper folder structure and tags for easy navigation.
