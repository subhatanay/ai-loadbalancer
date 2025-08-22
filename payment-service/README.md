# Payment Service

A comprehensive payment processing microservice for the AI Load Balancer E-commerce System.

## Features

- **Payment Processing**: Process payments with multiple payment methods
- **Refund Management**: Handle payment refunds with validation
- **Event-Driven Architecture**: Kafka integration for payment events
- **JWT Security**: Secure endpoints with JWT authentication
- **Redis Caching**: Cache payment data for improved performance
- **Service Discovery**: Automatic service registration with Redis
- **Monitoring**: Prometheus metrics and health checks
- **Database**: PostgreSQL for persistent storage

## API Endpoints

### Payment Operations
- `POST /api/payments` - Process a new payment
- `GET /api/payments/{paymentId}` - Get payment status
- `POST /api/payments/{paymentId}/refund` - Process refund

### Health & Monitoring
- `GET /health` - Service health check
- `GET /actuator/health` - Detailed health information
- `GET /actuator/metrics` - Service metrics
- `GET /actuator/prometheus` - Prometheus metrics

## Configuration

### Environment Variables
- `DB_HOST` - Database host (default: localhost)
- `DB_PORT` - Database port (default: 5432)
- `DB_NAME` - Database name (default: ecommerce)
- `DB_USERNAME` - Database username (default: ecommerce_user)
- `DB_PASSWORD` - Database password (default: ecommerce_pass)
- `REDIS_HOST` - Redis host (default: localhost)
- `REDIS_PORT` - Redis port (default: 6379)
- `KAFKA_BROKERS` - Kafka brokers (default: localhost:9092)
- `JWT_SECRET` - JWT signing secret
- `POD_IP` - Service IP for registration (default: localhost)

## Payment Methods Supported
- Credit Card
- Debit Card
- PayPal
- UPI
- Wallet
- Bank Transfer
- Cash on Delivery (COD)

## Payment Statuses
- `INITIATED` - Payment initiated
- `PROCESSING` - Payment being processed
- `COMPLETED` - Payment successful
- `FAILED` - Payment failed
- `REFUNDED` - Payment refunded
- `CANCELLED` - Payment cancelled

## Events Published
- `payment-processed` - When payment is completed
- `payment-refunded` - When refund is processed

## Build & Run

```bash
# Build the service
mvn clean compile

# Run the service
mvn spring-boot:run

# Run with custom profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Docker Support

```bash
# Build Docker image
docker build -t payment-service:latest .

# Run with Docker
docker run -p 8085:8085 payment-service:latest
```

## Integration

This service integrates with:
- **Order Service**: Receives payment requests from order processing
- **Service Registry**: Auto-registers with Redis-based service discovery
- **Load Balancer**: Participates in AI-powered load balancing
- **Monitoring**: Provides metrics to Prometheus/Grafana stack
