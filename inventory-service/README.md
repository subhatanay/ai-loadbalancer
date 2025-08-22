# Inventory Service

A comprehensive, production-ready inventory management microservice built with Spring Boot for the AI Load Balancer e-commerce system.

## 🚀 Features

- **Inventory Management**: Real-time inventory tracking and availability checking
- **Reservation System**: Time-based inventory reservations with automatic expiration
- **Stock Adjustment**: Manual stock adjustments with complete audit trail
- **Event-Driven Architecture**: Kafka-based event publishing for all inventory operations
- **Caching**: Redis-based caching for high-performance operations
- **Security**: JWT-based authentication and authorization
- **Monitoring**: Comprehensive metrics and health checks
- **Service Discovery**: Redis-based service registry integration

## 🏗️ Architecture

### Technology Stack
- **Framework**: Spring Boot 3.2.0
- **Database**: PostgreSQL with JPA/Hibernate
- **Cache**: Redis
- **Message Queue**: Apache Kafka
- **Security**: JWT with Spring Security
- **Monitoring**: Micrometer + Prometheus
- **Build Tool**: Maven
- **Java Version**: 17

### Core Components

#### Domain Models
- **Product**: Product catalog with SKU, name, category, brand, price
- **InventoryItem**: Stock levels per product per warehouse
- **InventoryReservation**: Time-based inventory reservations
- **InventoryTransaction**: Complete audit trail of all inventory movements
- **LowStockAlert**: Automated low stock notifications

#### Services
- **InventoryService**: Core business logic for inventory operations
- **InventoryValidationService**: Business rule validation
- **ReservationCleanupService**: Scheduled cleanup of expired reservations
- **InventoryEventPublisher**: Kafka event publishing
- **ServiceRegistryPublisher**: Service discovery integration

## 📋 Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL 12+
- Redis 6+
- Apache Kafka 2.8+

## 🛠️ Installation & Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd inventory-service
```

### 2. Database Setup
Create PostgreSQL database:
```sql
CREATE DATABASE inventory_db;
CREATE USER inventory_user WITH PASSWORD 'inventory_pass';
GRANT ALL PRIVILEGES ON DATABASE inventory_db TO inventory_user;
```

### 3. Environment Configuration
Set the following environment variables or update `application.yml`:

```bash
# Database Configuration
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=inventory_db
export DB_USERNAME=inventory_user
export DB_PASSWORD=inventory_pass

# Redis Configuration
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Kafka Configuration
export KAFKA_BROKERS=localhost:9092

# JWT Configuration
export JWT_SECRET=mySecretKey12345678901234567890123456789012345678901234567890

# Service Configuration
export SERVER_PORT=8084
export LOG_LEVEL=INFO
```

### 4. Build and Run

#### Using Maven
```bash
# Build the application
mvn clean package

# Run the application
mvn spring-boot:run
```

#### Using Docker
```bash
# Build Docker image
docker build -t inventory-service:latest .

# Run with Docker
docker run -p 8084:8084 \
  -e DB_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  -e KAFKA_BROKERS=host.docker.internal:9092 \
  inventory-service:latest
```

#### Using Docker Compose
```yaml
version: '3.8'
services:
  inventory-service:
    build: .
    ports:
      - "8084:8084"
    environment:
      - DB_HOST=postgres
      - REDIS_HOST=redis
      - KAFKA_BROKERS=kafka:9092
    depends_on:
      - postgres
      - redis
      - kafka
```

## 📚 API Documentation

### Base URL
```
http://localhost:8084/api/inventory
```

### Authentication
All endpoints (except health checks) require JWT authentication:
```
Authorization: Bearer <jwt-token>
```

### Endpoints

#### 1. Check Inventory Availability
```http
POST /api/inventory/check
Content-Type: application/json

{
  "productSku": "PROD-001",
  "quantity": 5,
  "warehouseLocation": "WAREHOUSE-A"
}
```

**Response:**
```json
{
  "productSku": "PROD-001",
  "available": true,
  "availableQuantity": 100,
  "requestedQuantity": 5,
  "warehouseLocation": "WAREHOUSE-A",
  "message": "Stock available"
}
```

#### 2. Reserve Inventory
```http
POST /api/inventory/reserve
Content-Type: application/json

{
  "orderId": "ORDER-123",
  "productSku": "PROD-001",
  "quantity": 5,
  "warehouseLocation": "WAREHOUSE-A",
  "reservationDurationMinutes": 30
}
```

**Response:**
```json
{
  "reservationId": "uuid-reservation-id",
  "orderId": "ORDER-123",
  "productSku": "PROD-001",
  "reservedQuantity": 5,
  "warehouseLocation": "WAREHOUSE-A",
  "status": "ACTIVE",
  "expiresAt": "2024-01-01T12:30:00",
  "createdAt": "2024-01-01T12:00:00",
  "success": true,
  "message": "Inventory reserved successfully"
}
```

#### 3. Release Reservation
```http
POST /api/inventory/release/{reservationId}
```

#### 4. Confirm Reservation
```http
POST /api/inventory/confirm/{reservationId}
```

#### 5. Adjust Stock
```http
POST /api/inventory/adjust
Content-Type: application/json

{
  "productSku": "PROD-001",
  "quantityAdjustment": 10,
  "warehouseLocation": "WAREHOUSE-A",
  "reason": "Stock replenishment",
  "performedBy": "admin"
}
```

#### 6. Get Inventory by Product SKU
```http
GET /api/inventory/product/{productSku}
```

#### 7. Get All Inventory Items
```http
GET /api/inventory/all
```

#### 8. Get Low Stock Items
```http
GET /api/inventory/low-stock
```

#### 9. Health Check
```http
GET /api/inventory/health
```

## 📊 Events Published

The service publishes the following Kafka events:

### Topics
- `inventory-reserved`: When inventory is reserved
- `inventory-released`: When reservation is released
- `inventory-confirmed`: When reservation is confirmed
- `inventory-adjusted`: When stock is manually adjusted
- `low-stock-alert`: When stock falls below threshold

### Event Structure
```json
{
  "data": {
    "eventId": "uuid",
    "productSku": "PROD-001",
    "quantity": 5,
    "timestamp": "2024-01-01T12:00:00"
  },
  "metadata": {
    "service": "inventory-service",
    "version": "1.0",
    "timestamp": "2024-01-01T12:00:00",
    "source": "inventory-event-publisher"
  }
}
```

## 🔧 Configuration

### Application Properties
Key configuration properties in `application.yml`:

```yaml
spring:
  application:
    name: inventory-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:inventory_db}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}

server:
  port: ${SERVER_PORT:8084}

app:
  jwt:
    secret: ${JWT_SECRET:defaultSecret}
    expiration: ${JWT_EXPIRATION:86400000}
```

### Cache Configuration
- **inventory-check**: 5 minutes TTL
- **inventory-items**: 15 minutes TTL
- **low-stock-items**: 10 minutes TTL
- **products**: 1 hour TTL

## 📈 Monitoring

### Health Checks
- **Application Health**: `/health`
- **Detailed Health**: `/actuator/health`

### Metrics
- **Prometheus Metrics**: `/actuator/prometheus`
- **Application Metrics**: `/actuator/metrics`

### Custom Metrics
- `inventory_check_requests_total`: Total inventory check requests
- `inventory_reservation_requests_total`: Total reservation requests
- `inventory_check_duration`: Time taken for inventory checks
- `inventory_reservation_duration`: Time taken for reservations

## 🔒 Security

### JWT Authentication
- All API endpoints require valid JWT tokens
- Tokens are validated using configured secret key
- User information is extracted from token claims

### Endpoint Security
- Health endpoints are publicly accessible
- All business endpoints require authentication
- Unauthorized requests return 401 status

## 🗄️ Database Schema

### Key Tables
- **products**: Product catalog
- **inventory_items**: Stock levels per product/warehouse
- **inventory_reservations**: Active and historical reservations
- **inventory_transactions**: Complete audit trail
- **low_stock_alerts**: Stock alert management

### Indexes
Optimized indexes for:
- Product SKU lookups
- Warehouse-based queries
- Reservation status filtering
- Transaction history queries

## 🔄 Scheduled Tasks

### Reservation Cleanup
- **Frequency**: Every 5 minutes
- **Purpose**: Clean up expired reservations
- **Action**: Release reserved stock back to available

### Old Record Cleanup
- **Frequency**: Daily at 2 AM
- **Purpose**: Clean up old reservation records
- **Retention**: 30 days

## 🚀 Deployment

### Docker Deployment
```bash
# Build image
docker build -t inventory-service:1.0.0 .

# Run container
docker run -d \
  --name inventory-service \
  -p 8084:8084 \
  -e DB_HOST=your-db-host \
  -e REDIS_HOST=your-redis-host \
  -e KAFKA_BROKERS=your-kafka-brokers \
  inventory-service:1.0.0
```

### Kubernetes Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: inventory-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: inventory-service
  template:
    metadata:
      labels:
        app: inventory-service
    spec:
      containers:
      - name: inventory-service
        image: inventory-service:1.0.0
        ports:
        - containerPort: 8084
        env:
        - name: DB_HOST
          value: "postgres-service"
        - name: REDIS_HOST
          value: "redis-service"
        - name: KAFKA_BROKERS
          value: "kafka-service:9092"
```

## 🧪 Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests
```bash
mvn verify
```

### API Testing with curl
```bash
# Health check
curl http://localhost:8084/health

# Check inventory (requires JWT)
curl -X POST http://localhost:8084/api/inventory/check \
  -H "Authorization: Bearer <jwt-token>" \
  -H "Content-Type: application/json" \
  -d '{"productSku":"PROD-001","quantity":5}'
```

## 📝 Logging

### Log Levels
- **INFO**: General application flow
- **WARN**: Potential issues
- **ERROR**: Error conditions
- **DEBUG**: Detailed debugging information

### Log Files
- **Location**: `logs/inventory-service.log`
- **Rotation**: 10MB max size, 30 days retention

## 🤝 Integration

### Service Dependencies
- **Order Service**: Consumes inventory events
- **Payment Service**: Triggers inventory confirmation
- **Cart Service**: Checks inventory availability

### Event Flow
1. **Order Creation** → Inventory Reservation
2. **Payment Success** → Inventory Confirmation
3. **Payment Failure** → Inventory Release
4. **Order Cancellation** → Inventory Release

## 🛠️ Troubleshooting

### Common Issues

#### Database Connection Issues
```bash
# Check database connectivity
psql -h localhost -U inventory_user -d inventory_db
```

#### Redis Connection Issues
```bash
# Check Redis connectivity
redis-cli -h localhost -p 6379 ping
```

#### Kafka Connection Issues
```bash
# Check Kafka topics
kafka-topics.sh --list --bootstrap-server localhost:9092
```

### Performance Tuning
- Adjust JVM heap size: `-Xmx2g -Xms1g`
- Tune database connection pool
- Configure Redis connection pool
- Optimize Kafka producer settings

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 👥 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📞 Support

For support and questions:
- Create an issue in the repository
- Contact the development team
- Check the troubleshooting section

---

**Version**: 1.0.0  
**Last Updated**: January 2024  
**Maintainer**: AI Load Balancer Team
