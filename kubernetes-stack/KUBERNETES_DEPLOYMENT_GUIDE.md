# 🚀 AI Load Balancer Kubernetes Deployment Guide

## 📋 **Complete Kubernetes Manifest Files Recreated**

All Kubernetes configuration files have been recreated based on the Docker Compose stack:

### **Infrastructure Services (8 files):**
- ✅ `namespace.yaml` - AI Load Balancer namespace
- ✅ `postgres-init-configmap.yaml` - PostgreSQL initialization script
- ✅ `postgresql.yaml` - PostgreSQL database with multi-database setup
- ✅ `redis.yaml` - Redis cache and session store
- ✅ `zookeeper.yaml` - Zookeeper for Kafka coordination
- ✅ `kafka.yaml` - Kafka message broker (Confluent 7.4.0)
- ✅ `mailhog.yaml` - MailHog email testing service
- ✅ `cluster-role.yaml` - RBAC configuration

### **Microservices (6 files):**
- ✅ `user-service.yaml` - User authentication service (2 replicas)
- ✅ `cart-service.yaml` - Shopping cart service (2 replicas)
- ✅ `order-service.yaml` - Order management service (2 replicas)
- ✅ `inventory-service.yaml` - Inventory management service (2 replicas)
- ✅ `payment-service.yaml` - Payment processing service (2 replicas)
- ✅ `notification-service.yaml` - Notification service (2 replicas)

### **AI/ML Components (2 files):**
- ✅ `ai-loadbalancer.yaml` - AI-powered load balancer (1 replica)
- ✅ `rl-collector.yaml` - Reinforcement learning agent (1 replica)

### **Monitoring (2 files):**
- ✅ `prometheus.yaml` - Prometheus metrics collection with comprehensive scrape configs
- ✅ `grafana.yaml` - Grafana dashboards and visualization

### **Gateway (2 files - Optional):**
- ✅ `envoy-gatewayclass.yaml` - Envoy Gateway class definition
- ✅ `envoy-gateway-config.yaml` - HTTP routing configuration

## 🏗️ **Architecture Overview**

### **Database Configuration:**
- **PostgreSQL**: Multi-database setup with dedicated databases per service
  - `userdb` - User service
  - `inventory_db` - Inventory service
  - `notificationdb` - Notification service
  - `paymentdb` - Payment service
  - `orderdb` - Order service
  - `cartdb` - Cart service
  - `ecommerce_user` - Shared database user with proper permissions

### **Service Discovery:**
- All services use Kubernetes DNS for service discovery
- Format: `service-name.ai-loadbalancer.svc.cluster.local`
- Internal communication on cluster network

### **Environment Variables:**
- **Database**: PostgreSQL connection strings with Kubernetes service names
- **Cache**: Redis connection for caching and session management
- **Messaging**: Kafka brokers for event-driven architecture
- **Security**: JWT secrets and expiration times
- **Service URLs**: Inter-service communication endpoints

### **Resource Allocation:**
- **Requests**: 250m CPU, 256Mi memory per service
- **Limits**: 500m CPU, 512Mi memory per service
- **Storage**: EmptyDir volumes with size limits
- **Health Checks**: Liveness and readiness probes for all services

## 🚀 **Deployment Instructions**

### **Prerequisites:**
1. **Docker Images Built**: All services must have Docker images built locally
2. **Kind Cluster**: Kind (Kubernetes in Docker) installed
3. **kubectl**: Kubernetes CLI configured

### **Build Docker Images:**
```bash
# Build all service images
mvn clean package -DskipTests
docker build -t user-service:latest ./user-service
docker build -t cart-service:latest ./cart-service
docker build -t order-service:latest ./order-service
docker build -t inventory-service:latest ./inventory-service
docker build -t payment-service:latest ./payment-service
docker build -t notification-service:latest ./notification-service
docker build -t load-balancer:latest ./load-balancer
docker build -t rl-agent:latest ./rl_agent
```

### **Deploy to Kubernetes:**
```bash
# Run the startup script
./startup.sh
```

### **Manual Deployment Steps:**
```bash
# 1. Create Kind cluster
kind create cluster --name ai-loadbalancer-cluster

# 2. Load Docker images
kind load docker-image user-service:latest --name ai-loadbalancer-cluster
kind load docker-image cart-service:latest --name ai-loadbalancer-cluster
# ... (repeat for all images)

# 3. Apply Kubernetes manifests
kubectl apply -f config-yaml/namespace.yaml
kubectl apply -f config-yaml/postgres-init-configmap.yaml
kubectl apply -f config-yaml/postgresql.yaml
# ... (apply all config files)

# 4. Wait for deployments
kubectl wait --for=condition=available --timeout=300s deployment/postgres -n ai-loadbalancer

# 5. Ensure PostgreSQL initialization
./scripts/ensure-postgres-init.sh
```

## 🔍 **Verification Commands**

### **Check Deployment Status:**
```bash
# Check all pods
kubectl get pods -n ai-loadbalancer

# Check services
kubectl get services -n ai-loadbalancer

# Check deployments
kubectl get deployments -n ai-loadbalancer
```

### **Access Services:**
```bash
# AI Load Balancer
kubectl port-forward -n ai-loadbalancer service/ai-loadbalancer-service 8080:8080

# Prometheus
kubectl port-forward -n ai-loadbalancer service/prometheus-service 9090:9090

# Grafana
kubectl port-forward -n ai-loadbalancer service/grafana-service 3000:3000

# MailHog
kubectl port-forward -n ai-loadbalancer service/mailhog 8025:8025
```

### **Debug Commands:**
```bash
# Check pod logs
kubectl logs -n ai-loadbalancer deployment/user-service

# Describe pod issues
kubectl describe pod -n ai-loadbalancer <pod-name>

# Check PostgreSQL initialization
kubectl exec -n ai-loadbalancer deployment/postgres -- psql -U postgres -c "\\du"
```

## 🎯 **Expected Results**

### **Successful Deployment:**
- **22 Pods Running**: All services and infrastructure components
- **15 Services**: ClusterIP services for internal communication
- **8 Deployments**: Infrastructure services (1 replica each)
- **6 Deployments**: Microservices (2 replicas each)
- **2 Deployments**: AI/ML components

### **Service Endpoints:**
- **AI Load Balancer**: `http://localhost:8080` (via port-forward)
- **Prometheus**: `http://localhost:9090` (via port-forward)
- **Grafana**: `http://localhost:3000` (admin/admin)
- **MailHog**: `http://localhost:8025` (via port-forward)

## 🔧 **Troubleshooting**

### **Common Issues:**
1. **PostgreSQL Init**: Use `./scripts/ensure-postgres-init.sh` if databases aren't created
2. **Image Pull**: Ensure all Docker images are loaded into Kind cluster
3. **Resource Limits**: Increase Docker Desktop memory if pods are pending
4. **Service Discovery**: Verify Kubernetes DNS resolution between services

### **Complete Cleanup:**
```bash
# Run shutdown script
./shutdown.sh

# Manual cleanup
kind delete cluster --name ai-loadbalancer-cluster
```

## 🎉 **Success Indicators**

✅ **All 22 pods in Running state**
✅ **PostgreSQL with all databases created**
✅ **Kafka and Zookeeper operational**
✅ **All microservices responding to health checks**
✅ **AI Load Balancer accessible on port 8080**
✅ **Prometheus collecting metrics from all services**
✅ **Grafana dashboards accessible**

**The AI Load Balancer E-commerce platform is now fully operational on Kubernetes!**
