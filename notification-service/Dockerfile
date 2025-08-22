# Production stage - copy pre-built JAR
FROM eclipse-temurin:17-jre

# Create non-root user
RUN groupadd -r notificationuser && useradd -r -g notificationuser notificationuser

# Set working directory
WORKDIR /app

# Copy the pre-built JAR from local target directory
COPY target/notification-service-1.0.0.jar notification-service.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R notificationuser:notificationuser /app

# Switch to non-root user
USER notificationuser

# Expose port
EXPOSE 8085

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8085/actuator/health || exit 1

# JVM tuning
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar notification-service.jar"]
