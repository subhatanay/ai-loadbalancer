# Cart Service - AI Load Balancer E-commerce System

## Overview
The Cart Service is a microservice responsible for managing shopping cart operations in the AI Load Balancer E-commerce system. It provides comprehensive cart management functionality with JWT authentication, event publishing, and Redis-based storage.

## Features

### 🛒 **Core Cart Operations**
- **Create Cart**: Automatic cart creation for users and anonymous sessions
- **Add Items**: Add products to cart with validation
- **Update Items**: Modify item quantities
- **Remove Items**: Remove specific items from cart
- **Clear Cart**: Empty entire cart
- **Cart Summary**: Get cart totals and item counts

### 🔐 **Authentication & Security**
- **JWT Authentication**: Secure token-based authentication
- **Session Management**: Support for both authenticated users and anonymous sessions
- **Security Filters**: Custom JWT authentication filter
- **Authorization**: Role-based access control

### 📊 **Event-Driven Architecture**
- **Kafka Integration**: Publishes cart events for AI load balancer analysis
- **Event Types**:
  - Cart Created
  - Item Added/Updated/Removed
  - Cart Cleared/Merged
  - Cart Converted to Order

### 🚀 **Performance & Monitoring**
- **Redis Caching**: High-performance cart storage with TTL
- **Metrics**: Prometheus metrics for monitoring
- **Health Checks**: Comprehensive health endpoints
- **Logging**: Structured logging with correlation IDs

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Load Balancer │────│  Cart Service   │────│     Redis       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │     Kafka       │
                       └─────────────────┘
```

## API Endpoints

### Cart Operations
- `GET /api/cart` - Get user's cart
- `POST /api/cart/items` - Add item to cart
- `PUT /api/cart/items` - Update cart item
- `DELETE /api/cart/items/{productId}` - Remove item from cart
- `DELETE /api/cart` - Clear entire cart
- `GET /api/cart/summary` - Get cart summary
- `POST /api/cart/merge` - Merge anonymous cart with user cart

### Internal Endpoints
- `POST /api/cart/{userId}/convert-to-order` - Convert cart to order (service-to-service)

### Health & Monitoring
- `GET /actuator/health` - Health check
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/prometheus` - Prometheus metrics

## Configuration

### Environment Variables
```yaml
# Redis Configuration
REDIS_HOST: localhost
REDIS_PORT: 6379

# Kafka Configuration
KAFKA_BROKERS: localhost:9092

# JWT Configuration
JWT_SECRET: your-secret-key
JWT_EXPIRATION: 86400000

# Service Configuration
POD_NAME: cart-service-pod
POD_IP: 127.0.0.1
```

### Application Properties
Key configurations in `application.yml`:
- Redis connection settings
- Kafka producer/consumer settings
- JWT authentication settings
- Logging configuration
- Metrics and monitoring settings

## Data Models

### Cart
```java
{
  "id": "cart:user:123",
  "userId": "user123",
  "sessionId": null,
  "items": [...],
  "totalAmount": 99.99,
  "totalItems": 3,
  "status": "ACTIVE",
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T10:30:00"
}
```

### CartItem
```java
{
  "productId": "prod123",
  "productName": "Sample Product",
  "productImage": "image-url",
  "price": 29.99,
  "quantity": 2,
  "subtotal": 59.98,
  "addedAt": "2024-01-01T10:15:00"
}
```

## Event Schema

### Cart Events Published to Kafka
- **cart-created**: When a new cart is created
- **cart-item-added**: When items are added to cart
- **cart-item-updated**: When item quantities change
- **cart-item-removed**: When items are removed
- **cart-cleared**: When cart is emptied
- **cart-merged**: When anonymous cart merges with user cart
- **cart-converted-to-order**: When cart becomes an order

## Security

### JWT Authentication
- Bearer token authentication
- User ID extraction from JWT claims
- Session-based fallback for anonymous users
- Secure endpoints with proper authorization

### Request Headers
```
Authorization: Bearer <jwt-token>
X-User-ID: <user-id> (optional, for service-to-service)
X-Session-ID: <session-id> (for anonymous users)
```

## Deployment

### Docker
```bash
# Build image
docker build -t cart-service:latest .

# Run container
docker run -p 8082:8082 \
  -e REDIS_HOST=redis \
  -e KAFKA_BROKERS=kafka:9092 \
  -e JWT_SECRET=your-secret \
  cart-service:latest
```

### Kubernetes
Deployment configurations available in `/kubernetes-stack` directory.

## Monitoring & Observability

### Metrics
- Cart operations count
- Response times
- Error rates
- Cache hit/miss ratios
- Kafka message publishing metrics

### Health Checks
- Application health
- Redis connectivity
- Kafka connectivity
- JWT service status

### Logging
- Structured JSON logging
- Request/response correlation
- Error tracking
- Performance monitoring

## Development

### Prerequisites
- Java 17+
- Maven 3.6+
- Redis 6+
- Kafka 2.8+

### Local Development
```bash
# Start dependencies
docker-compose up redis kafka

# Run application
mvn spring-boot:run

# Run tests
mvn test
```

### Testing
- Unit tests for service layer
- Integration tests for API endpoints
- Redis integration tests
- Kafka event publishing tests

## AI Load Balancer Integration

This service is specifically designed to work with the AI Load Balancer system:

1. **Event Publishing**: All cart operations publish events to Kafka for AI analysis
2. **Metrics Export**: Comprehensive metrics for load balancing decisions
3. **Performance Monitoring**: Real-time performance data for routing optimization
4. **Session Management**: Supports both authenticated and anonymous users for flexible routing

## Contributing

1. Follow Spring Boot best practices
2. Maintain comprehensive test coverage
3. Update documentation for API changes
4. Ensure proper error handling and logging
5. Follow security guidelines for JWT handling

## License

This project is part of the AI Load Balancer E-commerce System.
