# AI Load Balancer E-commerce Testing Guide

## Overview
This guide provides comprehensive testing instructions for the AI Load Balancer E-commerce platform, including API endpoint testing, business workflow validation, and integration testing.

## Prerequisites

### 1. Start the Complete Stack
```bash
cd kubernetes-stack
./startup.sh
```

Wait for all services to be ready (the script will show "All deployments are ready!").

### 2. Verify Services are Running
```bash
kubectl get pods -n default
```

All pods should show `Running` status with `2/2` ready containers.

### 3. Access URLs
- **Load Balancer:** http://localhost:8080
- **Prometheus:** http://localhost:9090  
- **Grafana:** http://localhost:3000 (admin/admin)

## Testing Methods

### Method 1: Postman Collection (Recommended)

#### Import Collection
1. Open Postman
2. Import `AI_LoadBalancer_API_Collection.postman_collection.json`
3. Set environment variables:
   - `base_url`: `http://localhost:8080/proxy`

#### Test Flow Sequence
Execute requests in this order:

1. **User Service → Register User**
2. **User Service → Login User** (saves JWT token)
3. **Cart Service → Add Item to Cart**
4. **Cart Service → Get Cart**
5. **Inventory Service → Check Inventory**
6. **Order Service → Create Order** (triggers saga)
7. **Payment Service → Get Payment Status**
8. **Notification Service → Get User Notifications**

### Method 2: cURL Commands

#### 1. Register User
```bash
curl -X POST http://localhost:8080/proxy/user-service/api/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe", 
    "email": "john.doe@example.com",
    "password": "SecurePassword123!",
    "phoneNumber": "+1234567890"
  }'
```

#### 2. Login User
```bash
curl -X POST http://localhost:8080/proxy/user-service/api/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "password": "SecurePassword123!"
  }'
```

Save the JWT token from response for subsequent requests.

#### 3. Add Item to Cart
```bash
curl -X POST http://localhost:8080/proxy/cart-service/api/cart/items \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "productId": "LAPTOP-001",
    "productName": "Gaming Laptop",
    "quantity": 1,
    "price": 1299.99
  }'
```

#### 4. Check Inventory
```bash
curl -X POST http://localhost:8080/proxy/inventory-service/api/inventory/check \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "productSku": "LAPTOP-001",
    "requestedQuantity": 1
  }'
```

#### 5. Create Order
```bash
curl -X POST http://localhost:8080/proxy/order-service/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "items": [
      {
        "productId": "LAPTOP-001",
        "productName": "Gaming Laptop",
        "quantity": 1,
        "unitPrice": 1299.99
      }
    ],
    "shippingAddress": {
      "street": "123 Main St",
      "city": "New York",
      "state": "NY", 
      "zipCode": "10001",
      "country": "USA"
    }
  }'
```

## Business Workflow Testing

### Complete E-commerce Flow Test

#### Test Scenario: Customer Purchase Journey
1. **User Registration & Authentication**
   - Register new user
   - Login and verify JWT token
   - Get user profile

2. **Shopping Cart Management**
   - Add multiple items to cart
   - Update item quantities
   - Get cart summary
   - Verify cart persistence

3. **Inventory Validation**
   - Check product availability
   - Verify stock levels
   - Test insufficient stock scenarios

4. **Order Processing (Saga Pattern)**
   - Create order with valid cart items
   - Verify inventory reservation
   - Confirm payment processing
   - Check cart clearing
   - Validate order status updates

5. **Payment Processing**
   - Process payment for order
   - Check payment status
   - Test payment failure scenarios
   - Verify refund functionality

6. **Notification System**
   - Verify order confirmation notifications
   - Check payment success notifications
   - Test multi-channel delivery

### Load Balancer Testing

#### Service Discovery Verification
```bash
# Check load balancer service discovery
curl http://localhost:8080/proxy/load-balancer/api/services
```

#### Traffic Distribution Testing
Execute the same request multiple times to verify load balancing:
```bash
for i in {1..10}; do
  curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
    http://localhost:8080/proxy/user-service/api/users/me \
    -H "X-User-Email: john.doe@example.com"
  echo "Request $i completed"
done
```

Check Prometheus metrics to verify traffic distribution across service instances.

## Error Scenario Testing

### 1. Authentication Errors
```bash
# Test without JWT token
curl http://localhost:8080/proxy/cart-service/api/cart

# Test with invalid JWT token  
curl -H "Authorization: Bearer invalid_token" \
  http://localhost:8080/proxy/cart-service/api/cart
```

### 2. Validation Errors
```bash
# Test with invalid email format
curl -X POST http://localhost:8080/proxy/user-service/api/users/register \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "email": "invalid-email",
    "password": "weak"
  }'
```

### 3. Resource Not Found
```bash
# Test with non-existent order ID
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8080/proxy/order-service/api/orders/99999
```

### 4. Service Unavailable
```bash
# Scale down a service to test circuit breaker
kubectl scale deployment cart-service --replicas=0

# Try to access cart service
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  http://localhost:8080/proxy/cart-service/api/cart

# Scale back up
kubectl scale deployment cart-service --replicas=2
```

## Performance Testing

### Load Testing with Apache Bench
```bash
# Install Apache Bench (if not installed)
brew install httpd  # macOS

# Test user login endpoint
ab -n 100 -c 10 -T 'application/json' \
  -p login_payload.json \
  http://localhost:8080/proxy/user-service/api/users/login
```

Create `login_payload.json`:
```json
{
  "email": "john.doe@example.com",
  "password": "SecurePassword123!"
}
```

### Stress Testing Order Creation
```bash
# Create multiple concurrent orders
for i in {1..5}; do
  (curl -X POST http://localhost:8080/proxy/order-service/api/orders \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer YOUR_JWT_TOKEN" \
    -d @order_payload.json &)
done
```

## Monitoring & Observability Testing

### 1. Prometheus Metrics
Visit http://localhost:9090 and query:
```promql
# Request rate by service
rate(http_requests_total[5m])

# Error rate
rate(http_requests_total{status=~"5.."}[5m])

# Response time percentiles
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))
```

### 2. Grafana Dashboards
Visit http://localhost:3000 (admin/admin) to view:
- Service health dashboards
- Request rate and latency metrics
- Error rate monitoring
- Resource utilization

### 3. Application Logs
```bash
# View logs for specific service
kubectl logs -f deployment/user-service

# View load balancer logs
kubectl logs -f deployment/ai-loadbalancer

# View all logs with labels
kubectl logs -l app=user-service --tail=100
```

## Integration Testing Checklist

### ✅ Service-to-Service Communication
- [ ] Order Service → Inventory Service (reservation)
- [ ] Order Service → Payment Service (processing)
- [ ] Order Service → Cart Service (clearing)
- [ ] Order Service → Notification Service (alerts)
- [ ] All services → Redis (service registry)
- [ ] All services → Kafka (event publishing)

### ✅ Data Consistency
- [ ] Cart items persist across sessions
- [ ] Inventory reservations expire correctly
- [ ] Order status updates propagate
- [ ] Payment transactions are atomic
- [ ] Notifications are delivered reliably

### ✅ Security Testing
- [ ] JWT token validation across services
- [ ] Authorization checks for protected endpoints
- [ ] Input validation and sanitization
- [ ] SQL injection prevention
- [ ] XSS protection

### ✅ Resilience Testing
- [ ] Service failure handling
- [ ] Circuit breaker functionality
- [ ] Retry mechanisms
- [ ] Graceful degradation
- [ ] Data backup and recovery

## Expected Results

### Successful Test Indicators
1. **All HTTP responses return appropriate status codes**
2. **JWT tokens are properly generated and validated**
3. **Business workflows complete end-to-end**
4. **Load balancer distributes traffic evenly**
5. **Metrics are collected and visible in Prometheus**
6. **Error scenarios are handled gracefully**
7. **Service discovery functions correctly**

### Common Issues & Solutions

#### Issue: Service Not Found
```
Error: No healthy instances available for service: user-service
```
**Solution:** Check if service is registered in Redis and pods are running

#### Issue: Authentication Failed
```
Error: JWT token is missing or invalid
```
**Solution:** Ensure JWT token is included in Authorization header

#### Issue: Database Connection Error
```
Error: Unable to connect to database
```
**Solution:** Verify PostgreSQL/MongoDB pods are running and accessible

#### Issue: Kafka Connection Error
```
Error: Failed to publish event to Kafka
```
**Solution:** Check Kafka and Zookeeper pod status

## Automated Testing Script

Create `test_all_endpoints.sh`:
```bash
#!/bin/bash

BASE_URL="http://localhost:8080/proxy"
JWT_TOKEN=""

# Function to test endpoint
test_endpoint() {
  local method=$1
  local endpoint=$2
  local data=$3
  local expected_status=$4
  
  echo "Testing: $method $endpoint"
  
  if [ -n "$data" ]; then
    response=$(curl -s -w "%{http_code}" -X $method \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $JWT_TOKEN" \
      -d "$data" \
      "$BASE_URL$endpoint")
  else
    response=$(curl -s -w "%{http_code}" -X $method \
      -H "Authorization: Bearer $JWT_TOKEN" \
      "$BASE_URL$endpoint")
  fi
  
  status_code="${response: -3}"
  
  if [ "$status_code" = "$expected_status" ]; then
    echo "✅ PASS: $endpoint returned $status_code"
  else
    echo "❌ FAIL: $endpoint returned $status_code, expected $expected_status"
  fi
  
  echo "---"
}

# Run tests
echo "Starting API endpoint tests..."

# Test user registration
test_endpoint "POST" "/user-service/api/users/register" \
  '{"firstName":"Test","lastName":"User","email":"test@example.com","password":"Test123!","phoneNumber":"+1234567890"}' \
  "201"

# Add more test calls here...

echo "All tests completed!"
```

Run with:
```bash
chmod +x test_all_endpoints.sh
./test_all_endpoints.sh
```

This comprehensive testing guide ensures all aspects of the AI Load Balancer E-commerce platform are thoroughly validated.
