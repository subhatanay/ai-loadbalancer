package com.bits.loadbalancer.model;

import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceMetrics implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("serviceUrl")
    private String serviceUrl;

    @JsonProperty("serviceName")
    private String serviceName;

    // Connection Metrics (Primary Focus)
    @JsonProperty("activeConnections")
    private int activeConnections;

    @JsonProperty("maxConnections")
    private int maxConnections;

    @JsonProperty("connectionUsagePercentage")
    private double connectionUsagePercentage;

    @JsonProperty("threadPoolActiveThreads")
    private int threadPoolActiveThreads;

    @JsonProperty("threadPoolMaxThreads")
    private int threadPoolMaxThreads;

    // Performance Metrics
    @JsonProperty("cpuUsage")
    private double cpuUsage;

    @JsonProperty("memoryUsage")
    private double memoryUsage;

    @JsonProperty("averageResponseTime")
    private double averageResponseTime;

    @JsonProperty("requestsPerSecond")
    private double requestsPerSecond;

    @JsonProperty("lastUpdated")
    private LocalDateTime lastUpdated;

    @JsonProperty("healthy")
    private boolean healthy;

    public ServiceMetrics() {
        this.lastUpdated = LocalDateTime.now();
        this.healthy = true;
    }

    public ServiceMetrics(String serviceUrl, String serviceName) {
        this.serviceUrl = serviceUrl;
        this.serviceName = serviceName;
        this.lastUpdated = LocalDateTime.now();
        this.healthy = true;
    }

    // Calculate connection load score (0-100, higher means more loaded)
    public double getConnectionLoadScore() {
        if (maxConnections <= 0) return 0.0;

        double connectionScore = (activeConnections * 100.0) / maxConnections;
        double threadScore = threadPoolMaxThreads > 0 ?
                (threadPoolActiveThreads * 100.0) / threadPoolMaxThreads : 0.0;

        // Weighted combination: 70% connection usage, 30% thread usage
        return (connectionScore * 0.7) + (threadScore * 0.3);
    }

    // Calculate overall load score including performance metrics
    public double getOverallLoadScore() {
        double connectionScore = getConnectionLoadScore();
        double performanceScore = (cpuUsage * 0.4) + (memoryUsage * 0.3) +
                (averageResponseTime / 10.0 * 0.3); // Normalize response time

        // Weighted combination: 60% connection, 40% performance
        return (connectionScore * 0.6) + (performanceScore * 0.4);
    }

    // Determine if service is available for new connections
    public boolean isAvailableForConnections() {
        return healthy && getConnectionLoadScore() < 90.0; // 90% threshold
    }

    public void setActiveConnections(int activeConnections) {
        this.activeConnections = activeConnections;
        updateConnectionUsagePercentage();
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        updateConnectionUsagePercentage();
    }

    private void updateConnectionUsagePercentage() {
        if (maxConnections > 0) {
            this.connectionUsagePercentage = (activeConnections * 100.0) / maxConnections;
        }
    }

    @Override
    public String toString() {
        return String.format("ServiceMetrics{url='%s', activeConn=%d/%d (%.1f%%), " +
                        "threads=%d/%d, cpu=%.1f%%, memory=%.1f%%, loadScore=%.1f, healthy=%s}",
                serviceUrl, activeConnections, maxConnections, connectionUsagePercentage,
                threadPoolActiveThreads, threadPoolMaxThreads, cpuUsage, memoryUsage,
                getOverallLoadScore(), healthy);
    }
}
