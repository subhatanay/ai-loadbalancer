# Dockerfile for User Service (and template for other services)
# Pre-built JAR packaging for optimal image size and security

# User Service - Pre-built JAR packaging
FROM eclipse-temurin:17-jre

# Add labels for better maintainability
LABEL org.opencontainers.image.title="User Service"
LABEL org.opencontainers.image.description="User management and authentication service"
LABEL org.opencontainers.image.version="1.0.0"

# Install necessary packages
RUN apt-get update && apt-get install -y \
    curl \
    tini \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -g 1001 usergroup && \
    useradd -u 1001 -g usergroup -s /bin/bash useruser

# Set working directory
WORKDIR /app

# Copy the pre-built JAR from local target directory
COPY target/user-service-0.0.1-SNAPSHOT.jar user-service.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R useruser:usergroup /app

# Switch to non-root user
USER useruser

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# Use tini as PID 1 to handle signals properly
ENTRYPOINT ["tini", "--"]

# Run the application
CMD ["java", \
     "-XX:+UseContainerSupport", \
     "-XX:MaxRAMPercentage=75.0", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-jar", \
     "user-service.jar"]
