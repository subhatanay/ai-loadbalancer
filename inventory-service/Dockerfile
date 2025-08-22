# Production stage - copy pre-built JAR
FROM eclipse-temurin:17-jre

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r inventoryuser && useradd -r -g inventoryuser inventoryuser

# Set working directory
WORKDIR /app

# Copy the pre-built JAR from local target directory
COPY target/inventory-service-1.0.0.jar app.jar

# Change ownership to non-root user
RUN chown -R inventoryuser:inventoryuser /app

# Switch to non-root user
USER inventoryuser

# Expose the correct port
EXPOSE 8084

# Add health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8084/health || exit 1

# JVM tuning for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
