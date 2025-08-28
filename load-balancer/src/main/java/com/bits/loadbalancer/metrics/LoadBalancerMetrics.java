package com.bits.loadbalancer.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized metrics collection for AI Load Balancer
 * Provides comprehensive monitoring of load balancing operations, RL decisions, and pod-level routing
 */
@Component
public class LoadBalancerMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // Load Balancer Request Processing Metrics
    private final Timer proxyRequestTimer;
    private final Counter proxyRequestsCounter;
    private final Counter proxyErrorsCounter;
    private final Gauge activeConnectionsGauge;
    private final AtomicLong activeConnections = new AtomicLong(0);
    
    // RL Agent Interaction Metrics
    private final Timer rlDecisionTimer;
    private final Counter rlDecisionsCounter;
    private final Counter rlFallbackCounter;
    private final Gauge rlAgentHealthGauge;
    private final DistributionSummary rlConfidenceScore;
    private final AtomicLong rlAgentHealthStatus = new AtomicLong(0); // 0=unhealthy, 1=healthy
    
    // Service-to-Pod Routing Metrics
    private final Counter podRequestsCounter;
    private final Timer podResponseTimer;
    private final Counter podErrorsCounter;
    private final ConcurrentHashMap<String, AtomicLong> activePodRequests = new ConcurrentHashMap<>();
    
    // RL Feedback Loop Metrics
    private final Counter rlFeedbackSentCounter;
    private final Counter rlFeedbackFailedCounter;
    private final DistributionSummary rlFeedbackResponseTime;
    
    public LoadBalancerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize Load Balancer Request Processing Metrics
        this.proxyRequestTimer = Timer.builder("lb_proxy_request_duration_seconds")
                .description("Time taken to process proxy requests")
                .tag("component", "load-balancer")
                .publishPercentileHistogram(true)
                .register(meterRegistry);
        
        this.proxyRequestsCounter = Counter.builder("lb_proxy_requests_total")
                .description("Total proxy requests by service")
                .tag("component", "load-balancer")
                .register(meterRegistry);
        
        this.proxyErrorsCounter = Counter.builder("lb_proxy_errors_total")
                .description("Total proxy errors by service and error type")
                .tag("component", "load-balancer")
                .register(meterRegistry);
        
        this.activeConnectionsGauge = Gauge.builder("lb_active_connections", activeConnections, AtomicLong::doubleValue)
                .description("Current active connections being processed")
                .tag("component", "load-balancer")
                .register(meterRegistry);
        
        // Initialize RL Agent Interaction Metrics
        this.rlDecisionTimer = Timer.builder("rl_decision_duration_seconds")
                .description("Time taken for RL agent decisions")
                .tag("component", "rl-agent")
                .publishPercentileHistogram(true)
                .register(meterRegistry);
        
        this.rlDecisionsCounter = Counter.builder("rl_decisions_total")
                .description("Total RL decisions by service and decision type")
                .tag("component", "rl-agent")
                .register(meterRegistry);
        
        this.rlFallbackCounter = Counter.builder("rl_fallback_total")
                .description("Total fallback decisions when RL unavailable")
                .tag("component", "rl-agent")
                .register(meterRegistry);
        
        this.rlAgentHealthGauge = Gauge.builder("rl_agent_health", rlAgentHealthStatus, AtomicLong::doubleValue)
                .description("RL agent health status (1=healthy, 0=unhealthy)")
                .tag("component", "rl-agent")
                .register(meterRegistry);
        
        this.rlConfidenceScore = DistributionSummary.builder("rl_confidence_score")
                .description("RL decision confidence scores")
                .tag("component", "rl-agent")
                .register(meterRegistry);
        
        // Initialize Service-to-Pod Routing Metrics
        this.podRequestsCounter = Counter.builder("lb_pod_requests_total")
                .description("Requests routed to specific pods")
                .tag("component", "load-balancer")
                .register(meterRegistry);
        
        this.podResponseTimer = Timer.builder("lb_pod_response_duration_seconds")
                .description("Response time from specific pods")
                .tag("component", "load-balancer")
                .publishPercentileHistogram(true)
                .register(meterRegistry);
        
        this.podErrorsCounter = Counter.builder("lb_pod_errors_total")
                .description("Errors from specific pods")
                .tag("component", "load-balancer")
                .register(meterRegistry);
        
        // Initialize RL Feedback Loop Metrics
        this.rlFeedbackSentCounter = Counter.builder("rl_feedback_sent_total")
                .description("Total feedback sent to RL agent")
                .tag("component", "rl-agent")
                .register(meterRegistry);
        
        this.rlFeedbackFailedCounter = Counter.builder("rl_feedback_failed_total")
                .description("Failed feedback attempts")
                .tag("component", "rl-agent")
                .register(meterRegistry);
        
        this.rlFeedbackResponseTime = DistributionSummary.builder("rl_feedback_response_time")
                .description("Response times reported in feedback")
                .tag("component", "rl-agent")
                .register(meterRegistry);
    }
    
    // Load Balancer Request Processing Methods
    public Timer.Sample startProxyRequest() {
        activeConnections.incrementAndGet();
        return Timer.start(meterRegistry);
    }
    
    public void recordProxyRequest(Timer.Sample sample, String serviceName, String status) {
        sample.stop(Timer.builder("lb_proxy_request_duration_seconds")
                .tag("service", serviceName)
                .tag("status", status)
                .publishPercentileHistogram(true)
                .register(meterRegistry));
        meterRegistry.counter("lb_proxy_requests_total", 
                "service", serviceName, 
                "status", status)
                .increment();
        activeConnections.decrementAndGet();
    }
    
    public void recordProxyError(String serviceName, String errorType, String statusCode) {
        meterRegistry.counter("lb_proxy_errors_total", 
                "service", serviceName, 
                "error_type", errorType, 
                "status", statusCode)
                .increment();
        activeConnections.decrementAndGet();
    }
    
    // RL Agent Interaction Methods
    public Timer.Sample startRLDecision() {
        return Timer.start(meterRegistry);
    }
    
    public void recordRLDecision(Timer.Sample sample, String serviceName, String decisionType, double confidence) {
        sample.stop(Timer.builder("rl_decision_duration_seconds")
                .tag("service", serviceName)
                .tag("decision_type", decisionType)
                .publishPercentileHistogram(true)
                .register(meterRegistry));
        meterRegistry.counter("rl_decisions_total", 
                "service", serviceName, 
                "decision_type", decisionType)
                .increment();
        rlConfidenceScore.record(confidence);
    }
    
    public void recordRLFallback(String serviceName, String reason) {
        meterRegistry.counter("rl_fallback_total", 
                "service", serviceName, 
                "reason", reason)
                .increment();
    }
    
    public void updateRLAgentHealth(boolean isHealthy) {
        rlAgentHealthStatus.set(isHealthy ? 1 : 0);
    }
    
    // Service-to-Pod Routing Methods
    public void recordPodRequest(String serviceName, String podName) {
        meterRegistry.counter("lb_pod_requests_total", 
                "service", serviceName, 
                "pod_name", podName)
                .increment();
        activePodRequests.computeIfAbsent(serviceName + ":" + podName, k -> {
            AtomicLong counter = new AtomicLong(0);
            Gauge.builder("lb_pod_active_requests", counter, AtomicLong::doubleValue)
                .description("Active requests per pod")
                .tag("service", serviceName)
                .tag("pod_name", podName)
                .register(meterRegistry);
            return counter;
        }).incrementAndGet();
    }
    
    public void recordPodResponse(String serviceName, String podName, long responseTimeMs, int statusCode) {
        Timer.builder("lb_pod_response_duration_seconds")
                .tag("service", serviceName)
                .tag("pod_name", podName)
                .tag("status", String.valueOf(statusCode))
                .publishPercentileHistogram(true)
                .register(meterRegistry)
                .record(responseTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        if (statusCode >= 400) {
            meterRegistry.counter("lb_pod_errors_total", 
                    "service", serviceName, 
                    "pod_name", podName, 
                    "status", String.valueOf(statusCode))
                    .increment();
        }
        
        // Decrement active requests
        AtomicLong activeCount = activePodRequests.get(serviceName + ":" + podName);
        if (activeCount != null) {
            activeCount.decrementAndGet();
        }
    }
    
    // RL Feedback Loop Methods
    public void recordFeedbackSent(String serviceName, String podName, double responseTimeMs) {
        meterRegistry.counter("rl_feedback_sent_total", 
                "service", serviceName, 
                "pod_name", podName)
                .increment();
        rlFeedbackResponseTime.record(responseTimeMs);
    }
    
    public void recordFeedbackFailed(String serviceName, String podName, String errorType) {
        meterRegistry.counter("rl_feedback_failed_total", 
                "service", serviceName, 
                "pod_name", podName, 
                "error_type", errorType)
                .increment();
    }
    
    // Utility Methods
    public long getActiveConnections() {
        return activeConnections.get();
    }
    
    public boolean isRLAgentHealthy() {
        return rlAgentHealthStatus.get() == 1;
    }
    
    /**
     * Check RL agent health based on recent decision activity
     * Updates health status automatically based on decision frequency
     */
    public void checkAndUpdateRLAgentHealth() {
        try {
            // Check if RL decisions have been made in the last 60 seconds
            double recentDecisions = meterRegistry.get("rl_decisions_total").counter().count();
            double recentFallbacks = meterRegistry.get("rl_fallback_total").counter().count();
            
            // Calculate health based on decision success rate
            double totalDecisions = recentDecisions + recentFallbacks;
            boolean isHealthy = totalDecisions > 0 && (recentDecisions / totalDecisions) > 0.5;
            
            updateRLAgentHealth(isHealthy);
        } catch (Exception e) {
            // If metrics are not available, assume unhealthy
            updateRLAgentHealth(false);
        }
    }
}
