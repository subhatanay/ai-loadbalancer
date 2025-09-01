package com.bits.loadbalancer.services;

import com.bits.commomutil.models.ServiceInfo;
import com.bits.loadbalancer.dao.ServiceRegistry;
import com.bits.loadbalancer.model.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Least Connections Load Balancer
 * Routes requests to the service instance with the fewest active connections.
 * Falls back to round-robin if connection metrics are unavailable.
 */
@org.springframework.stereotype.Service
public class LeastConnectionsLoadBalancer implements Loadbalancer {
    
    private static final Logger logger = LoggerFactory.getLogger(LeastConnectionsLoadBalancer.class);
    
    @Autowired
    private ServiceRegistry serviceRegistry;
    
    @Autowired
    private RoundRobinBasedLoadbalancer fallbackLoadBalancer;
    
    // Track active connections per instance URL
    private final Map<String, AtomicLong> activeConnections = new ConcurrentHashMap<>();
    
    @Override
    public ServiceInfo geNextServiceInstance(String serviceName) {
        Service service = serviceRegistry.getService(serviceName);
        if (service == null) {
            logger.warn("Service not found: {}", serviceName);
            return null;
        }
        
        List<ServiceInfo> healthyInstances = service.getHealthyInstances();
        if (healthyInstances.isEmpty()) {
            logger.warn("No healthy instances available for service: {}", serviceName);
            return null;
        }
        
        // Single instance - return directly
        if (healthyInstances.size() == 1) {
            return healthyInstances.get(0);
        }
        
        try {
            // Select instance with least active connections
            ServiceInfo selectedInstance = healthyInstances.stream()
                    .min(Comparator.comparing(this::getActiveConnections))
                    .orElse(null);
            
            if (selectedInstance != null) {
                // Increment connection count for selected instance
                incrementConnections(selectedInstance.getUrl());
                
                logger.debug("Selected instance {} for service {} with {} active connections", 
                           selectedInstance.getUrl(), serviceName, getActiveConnections(selectedInstance));
                return selectedInstance;
            } else {
                logger.warn("Could not select instance for service: {}, falling back to round-robin", serviceName);
                return fallbackLoadBalancer.geNextServiceInstance(serviceName);
            }
            
        } catch (Exception e) {
            logger.error("Error in least connections algorithm for service: {}, falling back to round-robin. Error: {}", 
                        serviceName, e.getMessage());
            return fallbackLoadBalancer.geNextServiceInstance(serviceName);
        }
    }
    
    /**
     * Get the current active connection count for an instance
     */
    private long getActiveConnections(ServiceInfo instance) {
        return activeConnections.computeIfAbsent(instance.getUrl(), k -> new AtomicLong(0)).get();
    }
    
    /**
     * Increment connection count for an instance
     */
    public void incrementConnections(String instanceUrl) {
        activeConnections.computeIfAbsent(instanceUrl, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * Decrement connection count for an instance (called when request completes)
     */
    public void decrementConnections(String instanceUrl) {
        AtomicLong count = activeConnections.get(instanceUrl);
        if (count != null) {
            count.decrementAndGet();
        }
    }
    
    /**
     * Get current connection counts for monitoring
     */
    public Map<String, Long> getConnectionCounts() {
        Map<String, Long> counts = new ConcurrentHashMap<>();
        activeConnections.forEach((url, count) -> counts.put(url, count.get()));
        return counts;
    }
}
