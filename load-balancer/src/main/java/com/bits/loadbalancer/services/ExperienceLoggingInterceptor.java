package com.bits.loadbalancer.services;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.bits.commomutil.models.ServiceInfo;

import com.bits.loadbalancer.clients.RLCollectorClient;
import com.bits.loadbalancer.configuration.CollectorProperties;
import com.bits.loadbalancer.dao.ServiceRegistry;
import com.bits.loadbalancer.dto.RLExperience;
import com.bits.loadbalancer.model.InstanceMetrics;
import com.bits.loadbalancer.model.Service;
import com.bits.loadbalancer.model.StateSnapshot;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.annotation.PreDestroy;

@Component
public class ExperienceLoggingInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(ExperienceLoggingInterceptor.class);

    @Autowired(required = false)
    private PrometheusMetricsService metricsService;
    
    @Autowired(required = false) 
    private RLCollectorClient collectorClient;
    
    @Autowired
    private CollectorProperties collectorProperties;
    
    
    @Autowired
    private ServiceRegistry serviceRegistry;
    
    // Simple circuit breaker
    private final AtomicBoolean prometheusEnabled = new AtomicBoolean(true);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private static final long FAILURE_TIMEOUT = 30000; // 30 seconds
    private static final int MAX_FAILURES = 5;
    
    // Simple async executor
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RL-Logger");
        t.setDaemon(true);
        return t;
    });

    public static final String PRE_SNAPSHOT = "preSnapshot";
    public static final String CHOSEN_INSTANCE = "chosenPodInstance";
    public static final String AVAILABLE_PODS = "availablePods";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            // Store minimal data - just timestamp and available pods
            request.setAttribute("rl_start_time", Instant.now());
            
            List<String> pods = fetchAvailablePods();
            request.setAttribute(AVAILABLE_PODS, pods);
            
            log.debug("RL preHandle: stored start time and {} pods", pods.size());
            
        } catch (Exception e) {
            log.debug("RL preHandle error (non-critical): {}", e.getMessage());
        }
        return true; // Always proceed - never block requests
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // Extract all needed data before async processing to avoid request recycling issues
        try {
            // Extract data from request attributes
            Instant startTime = (Instant) request.getAttribute("rl_start_time");
            final String chosenInstance = (String) request.getAttribute(CHOSEN_INSTANCE);
            
            @SuppressWarnings("unchecked")
            List<String> availablePods = (List<String>) request.getAttribute(AVAILABLE_PODS);
            if (availablePods == null) {
                availablePods = fetchAvailablePods();
            }
            final List<String> finalAvailablePods = availablePods;
            
            // Extract request data before recycling
            final String requestPath = request.getRequestURI();
            final int responseStatus = response.getStatus();
            
            if (startTime == null) {
                startTime = Instant.now().minusSeconds(1);
            }
            final Instant finalStartTime = startTime;
            
            // Submit async task with extracted data
            executor.submit(() -> {
                try {
                    logRLExperienceAsync(finalStartTime, chosenInstance, finalAvailablePods, requestPath, responseStatus);
                } catch (Exception e) {
                    log.warn("Error during async RL experience processing: {}", e.getMessage());
                }
            });
                    
        } catch (Exception e) {
            log.debug("RL experience logging failed (non-critical): {}", e.getMessage());
        }
    }
    
    private void logRLExperienceAsync(Instant startTime, String chosenInstance, List<String> availablePods, 
                                     String requestPath, int responseStatus) {
        try {
            // Create simple snapshots with minimal metrics
            Map<String, InstanceMetrics> emptyMetrics = new HashMap<>();
            
            // Try to get metrics if Prometheus is available
            if (isPrometheusAvailable() && metricsService != null) {
                try {
                    emptyMetrics = metricsService.fetchMetricsForPods(availablePods);
                    recordPrometheusSuccess();
                } catch (Exception e) {
                    recordPrometheusFailure();
                    log.debug("Prometheus unavailable, using empty metrics: {}", e.getMessage());
                    emptyMetrics = new HashMap<>();
                }
            }
            
            StateSnapshot preSnapshot = new StateSnapshot(startTime, emptyMetrics);
            StateSnapshot postSnapshot = new StateSnapshot(Instant.now(), emptyMetrics);

            // Simple reward based on response status
            double reward = calculateSimpleReward(responseStatus);

            RLExperience experience = new RLExperience(preSnapshot, chosenInstance, reward, postSnapshot, Map.of(
                    "path", requestPath,
                    "status", responseStatus,
                    "timestamp", Instant.now().toString()
            ));
            
            // Send to collector if enabled and available
            if (collectorProperties.isEnabled() && collectorClient != null) {
                collectorClient.sendExperience(experience);
                log.debug("RL experience sent to collector: {} -> {} (reward: {})", 
                        requestPath, chosenInstance, reward);
            } else if (!collectorProperties.isEnabled()) {
                log.debug("RL collector disabled - experience not sent to collector");
            }
            
        } catch (Exception e) {
            log.debug("Async RL experience logging failed (non-critical): {}", e.getMessage());
        }
    }

    private List<String> fetchAvailablePods() {
        return serviceRegistry.getAllServices()
                .stream()
                .map(Service::getInstances)
                .flatMap(List::stream)
                .map(ServiceInfo::getInstanceName)
                .toList();
    }

    /**
     * Simple reward calculation based on response status
     */
    private double calculateSimpleReward(int responseStatus) {
        if (responseStatus >= 500) {
            return -2.0; // Server error
        } else if (responseStatus >= 400) {
            return -1.0; // Client error
        } else if (responseStatus < 300) {
            return 1.0;  // Success
        } else {
            return 0.0;  // Redirect
        }
    }
    
    private boolean isPrometheusAvailable() {
        if (!prometheusEnabled.get()) {
            // Check if we should retry
            long timeSinceFailure = System.currentTimeMillis() - lastFailureTime.get();
            if (timeSinceFailure > FAILURE_TIMEOUT) {
                prometheusEnabled.set(true);
                failureCount.set(0);
                log.debug("Re-enabling Prometheus after timeout");
            }
        }
        return prometheusEnabled.get();
    }
    
    private void recordPrometheusFailure() {
        long failures = failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        if (failures >= MAX_FAILURES) {
            prometheusEnabled.set(false);
            log.warn("Disabling Prometheus queries after {} failures", failures);
        }
    }
    
    private void recordPrometheusSuccess() {
        failureCount.set(0);
        if (!prometheusEnabled.get()) {
            prometheusEnabled.set(true);
            log.info("Re-enabled Prometheus after successful query");
        }
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down RL experience logging");
        executor.shutdown();
    }
}
