# Cart Service - Pre-built JAR packaging
FROM eclipse-temurin:17-jre

# Add labels for better maintainability
LABEL org.opencontainers.image.title="Cart Service"
LABEL org.opencontainers.image.description="Shopping cart management service"
LABEL org.opencontainers.image.version="1.0.0"

# Install necessary packages
RUN apt-get update && apt-get install -y \
    curl \
    tini \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -g 1001 cartgroup && \
    useradd -u 1001 -g cartgroup -s /bin/bash cartuser

# Set working directory
WORKDIR /app

# Copy the pre-built JAR from local target directory
COPY target/cart-service-0.0.1-SNAPSHOT.jar cart-service.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R cartuser:cartgroup /app

# Switch to non-root user
USER cartuser

# Expose port
EXPOSE 8082

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8082/actuator/health || exit 1

# Use tini as PID 1 to handle signals properly
ENTRYPOINT ["tini", "--"]

# Run the application
CMD ["java", \
     "-XX:+UseContainerSupport", \
     "-XX:MaxRAMPercentage=75.0", \
     "-XX:+UseG1GC", \
     "-XX:+UseStringDeduplication", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-Dspring.profiles.active=docker", \
     "-jar", \
     "cart-service.jar"]
