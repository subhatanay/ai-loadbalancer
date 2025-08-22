package com.bits.loadbalancer.controllers;

import com.bits.loadbalancer.services.RLBasedLoadbalancer;
import com.bits.loadbalancer.services.RLApiLoadBalancer;
import com.bits.loadbalancer.services.RLModelLoader;
import com.bits.loadbalancer.services.RoutingStrategyAlgorithm;
import com.bits.loadbalancer.services.Loadbalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for monitoring and managing RL-based load balancing
 */
@RestController
@RequestMapping("/rl")
public class RLMonitoringController {
    
    private static final Logger logger = LoggerFactory.getLogger(RLMonitoringController.class);
    
    @Autowired
    private RLModelLoader rlModelLoader;
    
    @Autowired
    private RoutingStrategyAlgorithm routingStrategyAlgorithm;
    
    /**
     * Get RL model status and statistics
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getRLStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Current routing strategy
        String currentStrategy = routingStrategyAlgorithm.getRoutingStrategy();
        status.put("currentStrategy", currentStrategy);

        // Model status (only for rl-static)
        if ("rl-static".equals(currentStrategy)) {
            status.put("modelStats", rlModelLoader.getModelStats());
        }
        
        // RL routing statistics based on the type of RL implementation
        Loadbalancer currentLoadbalancer = routingStrategyAlgorithm.getLoadbalancer();
        
        if ("rl-based".equals(routingStrategyAlgorithm.getRoutingStrategy())) {
            if (currentLoadbalancer instanceof RLApiLoadBalancer) {
                RLApiLoadBalancer rlApiLoadbalancer = (RLApiLoadBalancer) currentLoadbalancer;
                status.put("routingType", "api-based");
                status.put("routingStats", rlApiLoadbalancer.getRoutingStats());
                status.put("rlApiHealth", rlApiLoadbalancer.isRLApiHealthy());
            } else if (currentLoadbalancer instanceof RLBasedLoadbalancer) {
                RLBasedLoadbalancer rlLoadbalancer = (RLBasedLoadbalancer) currentLoadbalancer;
                status.put("routingType", "static-model");
                status.put("routingStats", rlLoadbalancer.getRoutingStats());
            }
        } else if ("rl-static".equals(routingStrategyAlgorithm.getRoutingStrategy()) && 
                   currentLoadbalancer instanceof RLBasedLoadbalancer) {
            RLBasedLoadbalancer rlLoadbalancer = (RLBasedLoadbalancer) currentLoadbalancer;
            status.put("routingType", "static-model");
            status.put("routingStats", rlLoadbalancer.getRoutingStats());
        }
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Reload RL models
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reloadModels() {
        try {
            logger.info("Manual RL model reload requested");
            rlModelLoader.reloadModels();
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "RL models reloaded successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to reload RL models", e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to reload models: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Reset RL routing statistics
     */
    @PostMapping("/reset-stats")
    public ResponseEntity<Map<String, String>> resetStats() {
        try {
            Loadbalancer currentLoadbalancer = routingStrategyAlgorithm.getLoadbalancer();
            boolean resetPerformed = false;
            
            if ("rl-based".equals(routingStrategyAlgorithm.getRoutingStrategy())) {
                if (currentLoadbalancer instanceof RLApiLoadBalancer) {
                    // Note: RLApiLoadBalancer doesn't have resetStats method yet
                    // Statistics are managed by the external RL API
                    Map<String, String> response = new HashMap<>();
                    response.put("status", "info");
                    response.put("message", "API-based RL statistics are managed by the RL service");
                    return ResponseEntity.ok(response);
                } else if (currentLoadbalancer instanceof RLBasedLoadbalancer) {
                    RLBasedLoadbalancer rlLoadbalancer = (RLBasedLoadbalancer) currentLoadbalancer;
                    rlLoadbalancer.resetStats();
                    resetPerformed = true;
                }
            } else if ("rl-static".equals(routingStrategyAlgorithm.getRoutingStrategy()) && 
                       currentLoadbalancer instanceof RLBasedLoadbalancer) {
                RLBasedLoadbalancer rlLoadbalancer = (RLBasedLoadbalancer) currentLoadbalancer;
                rlLoadbalancer.resetStats();
                resetPerformed = true;
            }
            
            if (resetPerformed) {
                Map<String, String> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "RL routing statistics reset");
                return ResponseEntity.ok(response);
            } else {
                Map<String, String> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "RL-based routing is not currently active");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Failed to reset RL statistics", e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to reset statistics: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Get available routing strategies
     */
    @GetMapping("/strategies")
    public ResponseEntity<Map<String, Object>> getAvailableStrategies() {
        Map<String, Object> strategies = new HashMap<>();
        strategies.put("current", routingStrategyAlgorithm.getRoutingStrategy());
        strategies.put("available", new String[]{"round-robin", "connection-aware", "rl-based", "rl-static"});
        strategies.put("rlModelsReady", rlModelLoader.isModelsReady());
        
        // Add information about RL implementation types
        Map<String, String> rlTypes = new HashMap<>();
        rlTypes.put("rl-based", "API-based RL with external RL service");
        rlTypes.put("rl-static", "Static Q-table based RL");
        strategies.put("rlImplementations", rlTypes);
        
        return ResponseEntity.ok(strategies);
    }
}
