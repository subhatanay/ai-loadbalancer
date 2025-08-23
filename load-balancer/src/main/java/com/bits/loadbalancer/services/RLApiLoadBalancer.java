package com.bits.loadbalancer.services;

import com.bits.commomutil.models.ServiceInfo;
import com.bits.loadbalancer.dao.ServiceRegistry;
import com.bits.loadbalancer.metrics.LoadBalancerMetrics;
import com.bits.loadbalancer.model.Service;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

/**
 * RL-based load balancer using external RL Decision API
 * Provides intelligent routing decisions with continuous learning
 */
@org.springframework.stereotype.Service
public class RLApiLoadBalancer implements Loadbalancer {
    
    private static final Logger logger = LoggerFactory.getLogger(RLApiLoadBalancer.class);
    
    private final RLDecisionClient rlDecisionClient;
    private final RoundRobinBasedLoadbalancer fallbackLoadbalancer;
    private final ServiceRegistry serviceRegistry;
    private final LoadBalancerMetrics loadBalancerMetrics;
    
    // Statistics
    private volatile int totalDecisions = 0;
    private volatile int rlDecisions = 0;
    private volatile int fallbackDecisions = 0;
    private volatile boolean rlApiHealthy = false;
    
    @Autowired
    public RLApiLoadBalancer(RLDecisionClient rlDecisionClient,
                            RoundRobinBasedLoadbalancer fallbackLoadbalancer,
                            ServiceRegistry serviceRegistry,
                            LoadBalancerMetrics loadBalancerMetrics) {
        this.rlDecisionClient = rlDecisionClient;
        this.fallbackLoadbalancer = fallbackLoadbalancer;
        this.serviceRegistry = serviceRegistry;
        this.loadBalancerMetrics = loadBalancerMetrics;
        
        // Start health monitoring
        startHealthMonitoring();
        
        logger.info("RL API Load Balancer initialized with fallback support");
    }
    
    @Override
    public ServiceInfo geNextServiceInstance(String serviceName) {
        totalDecisions++;
        
        // Check if RL API is healthy
        if (!rlApiHealthy) {
            logger.debug("RL API unhealthy, using fallback for service: {}", serviceName);
            loadBalancerMetrics.recordRLFallback(serviceName, "rl_api_unhealthy");
            return useFallback(serviceName);
        }
        
        // Start RL decision timing
        Timer.Sample rlDecisionSample = loadBalancerMetrics.startRLDecision();
        
        try {
            // Get RL decision with timeout
            RLDecisionClient.RoutingDecision decision = rlDecisionClient
                    .getRoutingDecision(serviceName, null)
                    .timeout(Duration.ofMillis(2000))
                    .block();
            
            if (decision != null && decision.selectedPod != null) {
                // Find the selected service instance
                ServiceInfo selectedService = findServiceByPodName(serviceName, decision.selectedPod);
                
                if (selectedService != null) {
                    rlDecisions++;
                    
                    // Record successful RL decision metrics
                    loadBalancerMetrics.recordRLDecision(rlDecisionSample, serviceName, 
                            decision.decisionType != null ? decision.decisionType : "rl_decision", 
                            decision.confidence);
                    
                    logger.debug("RL decision: {} -> {} (confidence: {:.2f}, type: {})", 
                        serviceName, decision.selectedPod, decision.confidence, decision.decisionType);
                    return selectedService;
                } else {
                    logger.warn("RL selected pod {} not found for service {}", decision.selectedPod, serviceName);
                    loadBalancerMetrics.recordRLFallback(serviceName, "selected_pod_not_found");
                }
            } else {
                loadBalancerMetrics.recordRLFallback(serviceName, "no_decision_returned");
            }
            
        } catch (Exception e) {
            logger.warn("RL decision failed for service {}: {}", serviceName, e.getMessage());
            loadBalancerMetrics.recordRLFallback(serviceName, "rl_decision_exception");
        }
        
        // Fallback to round-robin
        return useFallback(serviceName);
    }
    
    /**
     * Provide feedback to RL agent about routing outcome
     */
    public void provideFeedback(String serviceName, String selectedPod, long responseTimeMs, 
                               int statusCode, boolean errorOccurred) {
        try {
            rlDecisionClient.provideFeedback(serviceName, selectedPod, responseTimeMs, statusCode, errorOccurred);
        } catch (Exception e) {
            logger.debug("Failed to provide feedback: {}", e.getMessage());
        }
    }
    
    /**
     * Get routing statistics
     */
    public RoutingStats getRoutingStats() {
        return new RoutingStats(
            totalDecisions,
            rlDecisions,
            fallbackDecisions,
            rlApiHealthy,
            totalDecisions > 0 ? (double) rlDecisions / totalDecisions : 0.0
        );
    }
    
    /**
     * Check if RL API is healthy
     */
    public boolean isRLApiHealthy() {
        return rlApiHealthy;
    }
    
    private ServiceInfo useFallback(String serviceName) {
        fallbackDecisions++;
        ServiceInfo fallbackService = fallbackLoadbalancer.geNextServiceInstance(serviceName);
        logger.debug("Fallback decision: {} -> {}", serviceName, 
            fallbackService != null ? fallbackService.getInstanceName() : "null");
        return fallbackService;
    }
    
    private ServiceInfo findServiceByPodName(String serviceName, String podName) {
        Service service = serviceRegistry.getService(serviceName);
        if (service == null) {
            return null;
        }
        
        return service.getHealthyInstances().stream()
                .filter(instance -> podName.equals(instance.getInstanceName()))
                .findFirst()
                .orElse(null);
    }
    
    private void startHealthMonitoring() {
        // Check RL API health every 5 seconds
        new Thread(() -> {
            while (true) {
                try {
                    boolean previousHealth = rlApiHealthy;
                    rlApiHealthy = rlDecisionClient.isHealthy()
                            .onErrorReturn(false)
                            .block();
                    
                    // Update health metrics when status changes
                    if (previousHealth != rlApiHealthy) {
                        loadBalancerMetrics.updateRLAgentHealth(rlApiHealthy);
                        logger.info("RL Agent health status changed: {}", rlApiHealthy ? "HEALTHY" : "UNHEALTHY");
                    }
                    
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    if (rlApiHealthy) {
                        rlApiHealthy = false;
                        loadBalancerMetrics.updateRLAgentHealth(false);
                        logger.warn("RL Agent health check failed, marking as unhealthy");
                    }
                }
            }
        }).start();
    }
    
    public static class RoutingStats {
        public final int totalDecisions;
        public final int rlDecisions;
        public final int fallbackDecisions;
        public final boolean rlApiHealthy;
        public final double rlSuccessRate;
        
        public RoutingStats(int totalDecisions, int rlDecisions, int fallbackDecisions, 
                           boolean rlApiHealthy, double rlSuccessRate) {
            this.totalDecisions = totalDecisions;
            this.rlDecisions = rlDecisions;
            this.fallbackDecisions = fallbackDecisions;
            this.rlApiHealthy = rlApiHealthy;
            this.rlSuccessRate = rlSuccessRate;
        }
    }
}
