package com.bits.loadbalancer.model;

import lombok.Data;

@Data
public class InstanceMetrics {
    private double cpuUsagePercent;
    private double jvmMemoryUsagePercent;
    private double uptimeSeconds;
    private double avgResponseTimeMs;
    private double errorRatePercent;
    private double requestRatePerSecond;

    public InstanceMetrics(double cpuUsagePercent,
                          double jvmMemoryUsagePercent,
                          double uptimeSeconds,
                          double avgResponseTimeMs,
                          double errorRatePercent,
                          double requestRatePerSecond) {
        this.cpuUsagePercent = cpuUsagePercent;
        this.jvmMemoryUsagePercent = jvmMemoryUsagePercent;
        this.uptimeSeconds = uptimeSeconds;
        this.avgResponseTimeMs = avgResponseTimeMs;
        this.errorRatePercent = errorRatePercent;
        this.requestRatePerSecond = requestRatePerSecond;
    }
}
