# Notification Service

A comprehensive, event-driven notification service built with Spring Boot that supports multi-channel delivery, user preferences, and template-based messaging within the AI Load Balancer ecosystem.

## 🚀 Features

### Core Capabilities
- **Multi-Channel Delivery**: EMAIL, SMS, PUSH, IN_APP, WEBHOOK
- **Event-Driven Architecture**: Kafka-based event processing
- **Template System**: Dynamic content with variable substitution
- **User Preferences**: Granular notification controls and frequency limits
- **Delivery Tracking**: Comprehensive status monitoring and retry logic
- **JWT Security**: Secure API access with user authentication
- **Caching**: Redis-based performance optimization
- **Metrics**: Micrometer integration for monitoring

### Notification Types
- `WELCOME` - User onboarding notifications
- `ORDER_CONFIRMATION` - Order placement confirmations
- `ORDER_SHIPPED` - Shipping updates
- `ORDER_DELIVERED` - Delivery confirmations
- `PAYMENT_SUCCESS` - Successful payment notifications
- `PAYMENT_FAILED` - Payment failure alerts
- `INVENTORY_LOW_STOCK` - Stock level warnings
- `CART_ABANDONMENT` - Cart reminder notifications
- `PROMOTIONAL` - Marketing and promotional content
- `SYSTEM_ALERT` - System-wide notifications
- `PASSWORD_RESET` - Security-related notifications

## 🏗️ Architecture

### Components
```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Controllers   │    │    Services      │    │   Repositories  │
│                 │    │                  │    │                 │
│ • Notification  │───▶│ • Notification   │───▶│ • Notification  │
│ • UserPrefs     │    │ • UserPreference │    │ • UserPrefs     │
│ • Templates     │    │ • Template       │    │ • Template      │
│ • Delivery      │    │ • Delivery       │    │ • Delivery      │
└─────────────────┘    │ • EventPublisher │    └─────────────────┘
                       └──────────────────┘
                                │
                       ┌──────────────────┐
                       │   External APIs  │
                       │                  │
                       │ • Email Provider │
                       │ • SMS Provider   │
                       │ • Push Provider  │
                       │ • Webhook Client │
                       └──────────────────┘
```

### Event Flow
```
User Action → Kafka Event → Notification Processing → Channel Delivery → Status Update
```

## 🛠️ Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17
- **Database**: PostgreSQL (JPA/Hibernate)
- **Cache**: Redis
- **Messaging**: Apache Kafka
- **Security**: JWT
- **Monitoring**: Micrometer
- **Build**: Maven
- **Testing**: JUnit 5

## 📋 Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 12+
- Redis 6+
- Apache Kafka 2.8+

## 🚀 Getting Started

### 1. Environment Setup

Create `application.yml` or set environment variables:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/notification_db
    username: ${DB_USERNAME:notification_user}
    password: ${DB_PASSWORD:notification_pass}
  
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
  
  kafka:
    bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

notification:
  email:
    provider: ${EMAIL_PROVIDER:smtp}
    smtp:
      host: ${SMTP_HOST:smtp.gmail.com}
      port: ${SMTP_PORT:587}
      username: ${SMTP_USERNAME}
      password: ${SMTP_PASSWORD}
  
  sms:
    provider: ${SMS_PROVIDER:twilio}
    twilio:
      account-sid: ${TWILIO_ACCOUNT_SID}
      auth-token: ${TWILIO_AUTH_TOKEN}
      from-number: ${TWILIO_FROM_NUMBER}

jwt:
  secret: ${JWT_SECRET:your-secret-key}
  expiration: ${JWT_EXPIRATION:86400000}
```

### 2. Database Setup

```sql
-- Create database
CREATE DATABASE notification_db;
CREATE USER notification_user WITH PASSWORD 'notification_pass';
GRANT ALL PRIVILEGES ON DATABASE notification_db TO notification_user;
```

### 3. Build and Run

```bash
# Build the service
mvn clean install -DskipTests

# Run the service
mvn spring-boot:run

# Or run the JAR
java -jar target/notification-service-1.0.0.jar
```

The service will start on port `8085` by default.

## 📚 API Documentation

### Core Endpoints

#### Notifications
- `POST /api/notifications` - Create notification
- `GET /api/notifications/{id}` - Get notification details
- `PUT /api/notifications/{id}` - Update notification
- `DELETE /api/notifications/{id}` - Cancel notification
- `GET /api/notifications/user/{userId}` - Get user notifications
- `GET /api/notifications/stats` - Get notification statistics

#### User Preferences
- `GET /api/user-preferences` - Get user preferences
- `POST /api/user-preferences` - Create preference
- `PUT /api/user-preferences/{id}` - Update preference
- `DELETE /api/user-preferences/{id}` - Delete preference
- `PUT /api/user-preferences/bulk` - Bulk update preferences

#### Templates
- `GET /api/templates` - List templates
- `POST /api/templates` - Create template
- `PUT /api/templates/{id}` - Update template
- `DELETE /api/templates/{id}` - Delete template

#### Delivery
- `GET /api/delivery/{notificationId}` - Get delivery status
- `POST /api/delivery/{deliveryId}/retry` - Retry delivery

### Request Examples

#### Create Notification
```bash
curl -X POST http://localhost:8085/api/notifications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "userId": "user123",
    "type": "ORDER_CONFIRMATION",
    "title": "Order Confirmed",
    "content": "Your order #{{orderId}} has been confirmed",
    "channels": ["EMAIL", "PUSH"],
    "variables": {
      "orderId": "ORD-12345",
      "customerName": "John Doe"
    },
    "scheduledAt": "2024-01-01T10:00:00"
  }'
```

#### Set User Preferences
```bash
curl -X POST http://localhost:8085/api/user-preferences \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "notificationType": "PROMOTIONAL",
    "channel": "EMAIL",
    "enabled": true,
    "frequencyLimitPerHour": 2,
    "frequencyLimitPerDay": 5,
    "quietHoursStart": "22:00",
    "quietHoursEnd": "08:00"
  }'
```

## 🔧 Configuration

### Notification Channels

#### Email Configuration
```yaml
notification:
  email:
    provider: smtp
    smtp:
      host: smtp.gmail.com
      port: 587
      username: your-email@gmail.com
      password: your-app-password
      auth: true
      starttls: true
```

#### SMS Configuration
```yaml
notification:
  sms:
    provider: twilio
    twilio:
      account-sid: YOUR_ACCOUNT_SID
      auth-token: YOUR_AUTH_TOKEN
      from-number: +1234567890
```

#### Push Notification Configuration
```yaml
notification:
  push:
    provider: firebase
    firebase:
      service-account-key: path/to/service-account.json
      project-id: your-firebase-project
```

### Kafka Topics

The service uses the following Kafka topics:
- `notification.created` - New notification events
- `notification.processed` - Processing completion events
- `notification.delivered` - Delivery confirmation events
- `notification.failed` - Delivery failure events

## 📊 Monitoring

### Health Checks
- `GET /actuator/health` - Service health status
- `GET /actuator/metrics` - Application metrics

### Key Metrics
- `notification.created.total` - Total notifications created
- `notification.delivered.total` - Total successful deliveries
- `notification.failed.total` - Total failed deliveries
- `notification.processing.duration` - Processing time
- `user.preferences.active` - Active user preferences

## 🧪 Testing

```bash
# Run unit tests
mvn test

# Run integration tests
mvn verify

# Run with coverage
mvn clean test jacoco:report
```

## 🚀 Deployment

### Docker
```dockerfile
FROM openjdk:17-jre-slim
COPY target/notification-service-1.0.0.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Kubernetes
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: notification-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: notification-service
  template:
    metadata:
      labels:
        app: notification-service
    spec:
      containers:
      - name: notification-service
        image: notification-service:1.0.0
        ports:
        - containerPort: 8085
        env:
        - name: DB_HOST
          value: "postgres-service"
        - name: REDIS_HOST
          value: "redis-service"
        - name: KAFKA_SERVERS
          value: "kafka-service:9092"
```

## 🔒 Security

### Authentication
All endpoints require JWT authentication. Include the token in the Authorization header:
```
Authorization: Bearer <your-jwt-token>
```

### User Context
The service extracts user information from JWT tokens to ensure users can only access their own notifications and preferences.

## 🐛 Troubleshooting

### Common Issues

1. **Database Connection Failed**
   - Check PostgreSQL is running
   - Verify connection credentials
   - Ensure database exists

2. **Kafka Connection Issues**
   - Verify Kafka broker is accessible
   - Check topic configurations
   - Review consumer group settings

3. **Email Delivery Failures**
   - Verify SMTP credentials
   - Check firewall settings
   - Review email provider limits

4. **Redis Connection Problems**
   - Ensure Redis server is running
   - Check network connectivity
   - Verify Redis configuration

### Logs
Check application logs for detailed error information:
```bash
tail -f logs/notification-service.log
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## 📄 License

This project is part of the AI Load Balancer ecosystem and follows the same licensing terms.

## 📞 Support

For issues and questions:
- Check the troubleshooting section
- Review application logs
- Contact the development team

---

**Note**: This service is part of the larger AI Load Balancer microservices architecture and integrates with other services for user management, orders, payments, and more.
