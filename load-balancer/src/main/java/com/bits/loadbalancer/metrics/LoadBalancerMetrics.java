package com.bits.loadbalancer.metrics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
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
        this.proxyRequestTimer = Timer.builder("lb_proxy_request_duration")
                .description("Time taken to process proxy requests")
                .tag("component", "load-balancer")
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
        this.rlDecisionTimer = Timer.builder("rl_decision_duration")
                .description("Time taken for RL agent decisions")
                .tag("component", "rl-agent")
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
        
        this.podResponseTimer = Timer.builder("lb_pod_response_time")
                .description("Response time from specific pods")
                .tag("component", "load-balancer")
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
        sample.stop(Timer.builder("lb_proxy_request_duration")
                .tag("service", serviceName)
                .tag("status", status)
                .register(meterRegistry));
        Counter.builder("lb_proxy_requests_total")
                .tag("service", serviceName)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
        activeConnections.decrementAndGet();
    }
    
    public void recordProxyError(String serviceName, String errorType, String statusCode) {
        Counter.builder("lb_proxy_errors_total")
                .tag("service", serviceName)
                .tag("error_type", errorType)
                .tag("status", statusCode)
                .register(meterRegistry)
                .increment();
        activeConnections.decrementAndGet();
    }
    
    // RL Agent Interaction Methods
    public Timer.Sample startRLDecision() {
        return Timer.start(meterRegistry);
    }
    
    public void recordRLDecision(Timer.Sample sample, String serviceName, String decisionType, double confidence) {
        sample.stop(Timer.builder("rl_decision_duration")
                .tag("service", serviceName)
                .tag("decision_type", decisionType)
                .register(meterRegistry));
        Counter.builder("rl_decisions_total")
                .tag("service", serviceName)
                .tag("decision_type", decisionType)
                .register(meterRegistry)
                .increment();
        rlConfidenceScore.record(confidence);
    }
    
    public void recordRLFallback(String serviceName, String reason) {
        Counter.builder("rl_fallback_total")
                .tag("service", serviceName)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }
    
    public void updateRLAgentHealth(boolean isHealthy) {
        rlAgentHealthStatus.set(isHealthy ? 1 : 0);
    }
    
    // Service-to-Pod Routing Methods
    public void recordPodRequest(String serviceName, String podName) {
        Counter.builder("lb_pod_requests_total")
                .tag("service", serviceName)
                .tag("pod_name", podName)
                .register(meterRegistry)
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
        Timer.builder("lb_pod_response_duration")
                .tag("service", serviceName)
                .tag("pod_name", podName)
                .tag("status", String.valueOf(statusCode))
                .register(meterRegistry)
                .record(responseTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        if (statusCode >= 400) {
            Counter.builder("lb_pod_errors_total")
                    .tag("service", serviceName)
                    .tag("pod_name", podName)
                    .tag("status", String.valueOf(statusCode))
                    .register(meterRegistry)
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
        Counter.builder("rl_feedback_sent_total")
                .tag("service", serviceName)
                .tag("pod_name", podName)
                .register(meterRegistry)
                .increment();
        rlFeedbackResponseTime.record(responseTimeMs);
    }
    
    public void recordFeedbackFailed(String serviceName, String podName, String errorType) {
        Counter.builder("rl_feedback_failed_total")
                .tag("service", serviceName)
                .tag("pod_name", podName)
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();
    }
    
    // Utility Methods
    public long getActiveConnections() {
        return activeConnections.get();
    }
    
    public boolean isRLAgentHealthy() {
        return rlAgentHealthStatus.get() == 1;
    }
}
