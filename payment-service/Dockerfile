# Payment Service - Pre-built JAR packaging
FROM eclipse-temurin:17-jre

# Add labels for better maintainability
LABEL org.opencontainers.image.title="Payment Service"
LABEL org.opencontainers.image.description="Payment processing and refund management service"
LABEL org.opencontainers.image.version="1.0.0"

# Install necessary packages
RUN apt-get update && apt-get install -y \
    curl \
    tini \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -g 1001 paymentgroup && \
    useradd -u 1001 -g paymentgroup -s /bin/bash paymentuser

# Set working directory
WORKDIR /app

# Copy the pre-built JAR from local target directory
COPY target/payment-service-1.0.0.jar payment-service.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R paymentuser:paymentgroup /app

# Switch to non-root user
USER paymentuser

# Expose port
EXPOSE 8085

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8085/actuator/health || exit 1

    
# Use tini as PID 1 to handle signals properly
ENTRYPOINT ["tini", "--"]

# Run the application
CMD ["java", \
     "-XX:+UseContainerSupport", \
     "-XX:MaxRAMPercentage=75.0", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-jar", \
     "payment-service.jar"]
