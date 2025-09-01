package com.bits.commomutil.models;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ServiceInfo implements Serializable {
    private String serviceName;
    private String instanceName;
    private String url;
    private String healthUrl;
    private boolean healthy;
    private LocalDateTime lastHealthCheck;
    private long responseTime;

    public ServiceInfo() {}

    public ServiceInfo(String serviceName, String url, String healthUrl, String instanceName) {
        this.serviceName = serviceName;
        this.url = url;
        this.healthUrl = healthUrl;
        this.healthy = true;
        this.lastHealthCheck = LocalDateTime.now();
        this.responseTime = 0;
        this.instanceName = instanceName;
    }

    public String getServiceName() {
        return serviceName;
    }
    // Getters and Setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getHealthUrl() { return healthUrl; }
    public void setHealthUrl(String healthUrl) { this.healthUrl = healthUrl; }

    public boolean isHealthy() { return healthy; }
    public void setHealthy(boolean healthy) { this.healthy = healthy; }

    public LocalDateTime getLastHealthCheck() { return lastHealthCheck; }
    public void setLastHealthCheck(LocalDateTime lastHealthCheck) { this.lastHealthCheck = lastHealthCheck; }

    public long getResponseTime() { return responseTime; }
    public void setResponseTime(long responseTime) { this.responseTime = responseTime; }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }
    @Override
    public String toString() {
        return String.format("ServiceInstance{url='%s', healthy=%s, lastCheck=%s}",
                url, healthy, lastHealthCheck);
    }
}