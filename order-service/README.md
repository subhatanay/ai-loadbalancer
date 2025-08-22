# Order Service

The Order Service is a comprehensive microservice responsible for managing e-commerce orders within the AI Load Balancer system. It provides complete order lifecycle management, saga orchestration for distributed transactions, and event-driven architecture integration.

## Features

### Core Functionality
- **Order Management**: Complete CRUD operations for orders
- **Order Lifecycle**: Status tracking from creation to delivery
- **Saga Orchestration**: Distributed transaction management across services
- **Event Publishing**: Kafka-based event streaming for order activities
- **JWT Authentication**: Secure API endpoints with token-based authentication
- **Service Discovery**: Redis-based service registration and heartbeat

### Advanced Features
- **Distributed Transactions**: Saga pattern implementation for inventory, payment, and cart coordination
- **Caching**: Redis-based caching for improved performance
- **Validation**: Comprehensive request validation and business rule enforcement
- **Monitoring**: Prometheus metrics and health checks
- **Error Handling**: Global exception handling with detailed error responses

## Architecture

### Domain Model
```
Order
├── OrderItem (1:N)
├── ShippingAddress (embedded)
├── OrderStatus (enum)
└── PaymentStatus (enum)
```

### Service Components
- **OrderService**: Core business logic and CRUD operations
- **OrderSagaOrchestrator**: Distributed transaction coordination
- **OrderValidationService**: Business rule validation
- **OrderEventPublisher**: Kafka event publishing
- **ServiceRegistryPublisher**: Service discovery integration

### External Integrations
- **Inventory Service**: Stock reservation and release
- **Payment Service**: Payment processing and refunds
- **Cart Service**: Cart clearing after order completion

## API Endpoints

### Order Management
```http
POST   /api/orders                    # Create new order
GET    /api/orders/{id}               # Get order by ID
GET    /api/orders/number/{number}    # Get order by number
GET    /api/orders/user               # Get user's orders
GET    /api/orders/user/paginated     # Get paginated user orders
PUT    /api/orders/{id}/status        # Update order status
POST   /api/orders/{id}/cancel        # Cancel order
GET    /api/orders/status/{status}    # Get orders by status
GET    /api/orders/health             # Health check
```

### Request/Response Examples

#### Create Order
```json
POST /api/orders
{
  "items": [
    {
      "productId": "PROD-001",
      "productName": "Laptop",
      "productImage": "laptop.jpg",
      "price": 999.99,
      "quantity": 1
    }
  ],
  "shippingAddress": {
    "street": "123 Main St",
    "city": "New York",
    "state": "NY",
    "zipCode": "10001",
    "country": "USA",
    "phone": "+1234567890"
  },
  "notes": "Handle with care"
}
```

#### Order Response
```json
{
  "id": 1,
  "orderNumber": "ORD-1704097200000-A1B2C3D4",
  "userId": "user123",
  "userEmail": "user@example.com",
  "items": [...],
  "totalAmount": 999.99,
  "totalItems": 1,
  "status": "PENDING",
  "paymentStatus": "PENDING",
  "shippingAddress": {...},
  "createdAt": "2024-01-01T10:00:00",
  "updatedAt": "2024-01-01T10:00:00"
}
```

## Order Status Flow

```
PENDING → CONFIRMED → PAYMENT_PROCESSING → PAYMENT_COMPLETED → 
INVENTORY_RESERVED → PROCESSING → SHIPPED → DELIVERED

Alternative flows:
PENDING → CANCELLED
PAYMENT_PROCESSING → PAYMENT_FAILED → CANCELLED
INVENTORY_RESERVED → INVENTORY_FAILED → CANCELLED
```

## Saga Orchestration

### Order Processing Saga
1. **Inventory Reservation**: Reserve stock for order items
2. **Payment Processing**: Process payment for the order
3. **Cart Clearing**: Clear user's cart after successful payment
4. **Order Completion**: Mark order as processing

### Compensation Logic
- **Payment Failure**: Release reserved inventory
- **Inventory Failure**: No compensation needed (payment not processed)
- **Order Cancellation**: Refund payment and release inventory

## Event Publishing

### Published Events
- `ORDER_CREATED`: New order created
- `ORDER_STATUS_UPDATED`: Order status changed
- `ORDER_CANCELLED`: Order cancelled
- `ORDER_INVENTORY_RESERVED`: Inventory reserved
- `ORDER_PAYMENT_COMPLETED`: Payment processed
- `ORDER_PROCESSING_COMPLETED`: Saga completed successfully

### Event Structure
```json
{
  "eventType": "ORDER_CREATED",
  "timestamp": "2024-01-01T10:00:00",
  "orderId": 1,
  "orderNumber": "ORD-1704097200000-A1B2C3D4",
  "userId": "user123",
  "totalAmount": 999.99,
  "sagaId": "saga-uuid"
}
```

## Configuration

### Environment Variables
```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/orderdb
SPRING_DATASOURCE_USERNAME=orderuser
SPRING_DATASOURCE_PASSWORD=orderpass

# Redis
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379
SPRING_REDIS_PASSWORD=redispass

# Kafka
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_TOPICS_ORDER_EVENTS=order-events

# JWT
JWT_SECRET=your-secret-key
JWT_EXPIRATION=86400000

# Service URLs
SERVICES_INVENTORY_SERVICE_URL=http://localhost:8083
SERVICES_PAYMENT_SERVICE_URL=http://localhost:8084
SERVICES_CART_SERVICE_URL=http://localhost:8081
```

### Application Properties
```yaml
server:
  port: 8082

spring:
  application:
    name: order-service
  
  datasource:
    url: jdbc:postgresql://localhost:5432/orderdb
    username: ${DB_USERNAME:orderuser}
    password: ${DB_PASSWORD:orderpass}
  
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
  
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
  
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

jwt:
  secret: ${JWT_SECRET:default-secret-key}
  expiration: ${JWT_EXPIRATION:86400000}

services:
  inventory-service:
    url: ${INVENTORY_SERVICE_URL:http://localhost:8083}
  payment-service:
    url: ${PAYMENT_SERVICE_URL:http://localhost:8084}
  cart-service:
    url: ${CART_SERVICE_URL:http://localhost:8081}

kafka:
  topics:
    order-events: ${KAFKA_TOPIC_ORDER_EVENTS:order-events}
```

## Security

### JWT Authentication
- All endpoints except health checks require JWT authentication
- JWT tokens must be passed in the `Authorization` header as `Bearer <token>`
- User ID and email are extracted from JWT claims

### Security Features
- Stateless session management
- CORS configuration for cross-origin requests
- Input validation and sanitization
- SQL injection prevention through JPA

## Monitoring & Observability

### Health Checks
- Application health: `/api/orders/health`
- Actuator endpoints: `/actuator/health`, `/actuator/metrics`

### Metrics
- Order creation rate
- Order processing time
- Saga success/failure rates
- Cache hit/miss ratios

### Logging
- Structured logging with correlation IDs
- Request/response logging
- Error tracking and alerting

## Development

### Prerequisites
- Java 21+
- Maven 3.9+
- PostgreSQL 13+
- Redis 6+
- Kafka 2.8+

### Local Development
```bash
# Clone repository
git clone <repository-url>
cd order-service

# Install dependencies
mvn clean install

# Run tests
mvn test

# Start application
mvn spring-boot:run
```

### Docker Deployment
```bash
# Build image
docker build -t order-service:latest .

# Run container
docker run -p 8082:8082 \
  -e DB_URL=jdbc:postgresql://host:5432/orderdb \
  -e REDIS_HOST=redis-host \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  order-service:latest
```

## Testing

### Unit Tests
- Service layer testing with mocked dependencies
- Repository layer testing with @DataJpaTest
- Controller testing with @WebMvcTest

### Integration Tests
- End-to-end API testing
- Database integration testing
- Kafka event publishing testing

### Test Coverage
- Minimum 80% code coverage requirement
- Critical path testing for saga orchestration
- Error scenario testing

## Troubleshooting

### Common Issues

1. **Database Connection Issues**
   - Verify PostgreSQL is running
   - Check connection string and credentials
   - Ensure database exists

2. **Redis Connection Issues**
   - Verify Redis server is running
   - Check Redis host and port configuration
   - Validate Redis password if authentication is enabled

3. **Kafka Connection Issues**
   - Ensure Kafka broker is running
   - Verify bootstrap servers configuration
   - Check topic creation and permissions

4. **JWT Authentication Issues**
   - Verify JWT secret configuration
   - Check token expiration settings
   - Validate token format and claims

### Performance Tuning
- Adjust JVM heap size based on load
- Configure connection pool sizes
- Tune Redis cache TTL settings
- Optimize database queries and indexes

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make changes with appropriate tests
4. Submit a pull request with detailed description

## License

This project is licensed under the MIT License - see the LICENSE file for details.
