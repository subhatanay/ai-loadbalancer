# 🚀 AI Load Balancer Kubernetes Scripts

This directory contains all the scripts needed to build, deploy, and manage your AI Load Balancer Kubernetes deployment.

## 📋 Available Scripts

### 🏗️ `build.sh` - Build Script
**Purpose**: Builds all Maven projects and Docker images with correct naming for Kubernetes.

**What it does**:
- ✅ Cleans previous Maven builds (`mvn clean`)
- ✅ Builds all Spring Boot services (`mvn package -DskipTests`)
- ✅ Creates Docker images with correct tags:
  - `user-service:latest`
  - `cart-service:latest`
  - `order-service:latest`
  - `inventory-service:latest`
  - `payment-service:latest`
  - `notification-service:latest`
  - `load-balancer:latest`
  - `rl-agent:latest`
- ✅ Verifies all images are built successfully
- ✅ Shows image sizes and build summary

**Usage**:
```bash
./build.sh
```

### 🚀 `startup.sh` - Kubernetes Deployment Script
**Purpose**: Deploys the complete AI Load Balancer stack to Kubernetes.

**What it does**:
- ✅ Creates Kind cluster (`ai-loadbalancer-cluster`)
- ✅ Loads all Docker images into Kind cluster
- ✅ Applies all Kubernetes manifests (20 config files)
- ✅ Waits for deployments to be ready
- ✅ Runs PostgreSQL initialization script
- ✅ Provides access instructions

**Usage**:
```bash
./startup.sh
```

### 🛑 `shutdown.sh` - Cleanup Script
**Purpose**: Completely removes the Kubernetes deployment and cluster.

**What it does**:
- ✅ Deletes ai-loadbalancer namespace (all pods, services, deployments)
- ✅ Cleans up cluster-wide resources (RBAC, Envoy Gateway)
- ✅ Deletes entire Kind cluster
- ✅ Cleans up Docker resources (optional)
- ✅ Provides 26GB+ of disk space cleanup

**Usage**:
```bash
./shutdown.sh
```

### 🎯 `deploy.sh` - Complete Build & Deploy Script
**Purpose**: One-command build and deployment with advanced options.

**What it does**:
- ✅ Optional clean shutdown before deployment
- ✅ Builds all services and Docker images
- ✅ Deploys to Kubernetes
- ✅ Performs post-deployment verification
- ✅ Shows pod status and access instructions

**Usage**:
```bash
# Full build and deploy
./deploy.sh

# Clean shutdown, build, and deploy
./deploy.sh --clean

# Build only (skip deployment)
./deploy.sh --skip-deploy

# Deploy only (skip build)
./deploy.sh --skip-build

# Show help
./deploy.sh --help
```

## 🔧 Helper Scripts

### `scripts/ensure-postgres-init.sh`
**Purpose**: Ensures PostgreSQL databases are properly initialized.

**What it does**:
- ✅ Waits for PostgreSQL to be ready
- ✅ Checks if `ecommerce_user` exists
- ✅ Runs initialization script if needed
- ✅ Creates all required databases

## 📁 Configuration Files

### `config-yaml/` Directory (20 files)
Contains all Kubernetes manifest files:

**Infrastructure (8 files)**:
- `namespace.yaml` - AI Load Balancer namespace
- `postgres-init-configmap.yaml` - Database initialization
- `postgresql.yaml` - PostgreSQL deployment
- `redis.yaml` - Redis cache
- `zookeeper.yaml` - Zookeeper for Kafka
- `kafka.yaml` - Kafka message broker
- `mailhog.yaml` - Email testing
- `cluster-role.yaml` - RBAC configuration

**Microservices (6 files)**:
- `user-service.yaml` - User authentication (2 replicas)
- `cart-service.yaml` - Shopping cart (2 replicas)
- `order-service.yaml` - Order management (2 replicas)
- `inventory-service.yaml` - Inventory management (2 replicas)
- `payment-service.yaml` - Payment processing (2 replicas)
- `notification-service.yaml` - Notifications (2 replicas)

**AI/ML Components (2 files)**:
- `ai-loadbalancer.yaml` - AI-powered load balancer
- `rl-collector.yaml` - Reinforcement learning agent

**Monitoring (2 files)**:
- `prometheus.yaml` - Metrics collection
- `grafana.yaml` - Dashboards

**Gateway (2 files)**:
- `envoy-gatewayclass.yaml` - Gateway class
- `envoy-gateway-config.yaml` - HTTP routing

## 🎯 Quick Start Workflows

### **Complete Fresh Deployment**:
```bash
./deploy.sh --clean
```

### **Development Workflow**:
```bash
# Make code changes...
./build.sh                    # Build new images
./deploy.sh --skip-build      # Deploy with new images
```

### **Testing Workflow**:
```bash
./shutdown.sh                 # Clean environment
./deploy.sh                   # Fresh deployment
# Run tests...
./shutdown.sh                 # Cleanup
```

### **Production Deployment**:
```bash
./build.sh                    # Build all images
./startup.sh                  # Deploy to Kubernetes
# Monitor and verify...
```

## 🔍 Verification Commands

```bash
# Check all pods
kubectl get pods -n ai-loadbalancer

# Check services
kubectl get services -n ai-loadbalancer

# Check logs
kubectl logs -n ai-loadbalancer deployment/ai-loadbalancer

# Access services (run in separate terminals)
kubectl port-forward -n ai-loadbalancer service/ai-loadbalancer-service 8080:8080
kubectl port-forward -n ai-loadbalancer service/prometheus-service 9090:9090
kubectl port-forward -n ai-loadbalancer service/grafana-service 3000:3000
```

## 🎉 Expected Results

**Successful Deployment**:
- ✅ 22 pods running (infrastructure + microservices)
- ✅ All services accessible via port-forwarding
- ✅ PostgreSQL with all databases initialized
- ✅ Kafka and messaging operational
- ✅ AI Load Balancer routing traffic
- ✅ Monitoring stack (Prometheus/Grafana) running

**Service URLs** (via port-forward):
- 🌐 AI Load Balancer: http://localhost:8080
- 📊 Prometheus: http://localhost:9090
- 📈 Grafana: http://localhost:3000 (admin/admin)
- 📧 MailHog: http://localhost:8025

## 🆘 Troubleshooting

**Build Issues**:
- Ensure Maven and Docker are installed
- Check Dockerfile paths in each service directory
- Verify JAR files are built in target/ directories

**Deployment Issues**:
- Run `kubectl get pods -n ai-loadbalancer` to check pod status
- Use `kubectl describe pod <pod-name> -n ai-loadbalancer` for details
- Check logs with `kubectl logs <pod-name> -n ai-loadbalancer`

**Database Issues**:
- Run `./scripts/ensure-postgres-init.sh` manually
- Check PostgreSQL logs: `kubectl logs deployment/postgres -n ai-loadbalancer`

**Complete Reset**:
```bash
./shutdown.sh
./deploy.sh --clean
```

---

🎯 **The AI Load Balancer platform is now ready for development, testing, and production deployment!**
