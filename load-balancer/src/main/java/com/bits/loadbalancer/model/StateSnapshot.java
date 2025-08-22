package com.bits.loadbalancer.model;

import java.time.Instant;
import java.util.Map;

public class StateSnapshot {
    private Instant timestamp;
    private Map<String, InstanceMetrics> metrics;

    public StateSnapshot(Instant timestamp, Map<String, InstanceMetrics> serviceMetrics) {
        this.timestamp = timestamp;
        this.metrics = serviceMetrics;
    }

    public Instant getTimestamp() { return timestamp; }
    public Map<String, InstanceMetrics> getMetrics() { return metrics; }
}
