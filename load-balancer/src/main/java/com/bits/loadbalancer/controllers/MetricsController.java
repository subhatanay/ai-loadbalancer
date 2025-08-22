package com.bits.loadbalancer.controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.bits.loadbalancer.dao.ServiceRegistry;
import com.bits.loadbalancer.model.InstanceMetrics;
import com.bits.loadbalancer.model.Service;
import com.bits.loadbalancer.services.PrometheusMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    @Autowired
    private PrometheusMetricsService prometheusMetricsService;

    @Autowired
    private ServiceRegistry serviceRegistry;
//
////    @GetMapping
////    public ResponseEntity<Map<String, Object>> getAllMetrics() {
////        Map<String, Object> response = new HashMap<>();
////        Map<String, ServiceMetrics> allMetrics = new HashMap<>();
////
////        serviceRegistry.getAllServices().forEach(service -> {
////            service.getInstances().forEach(instance -> {
////                if (instance.isHealthy()) {
////                    try {
////                        String serviceName = service.getName();
////                        ServiceMetrics metrics = prometheusMetricsService.getServiceMetrics(
////                                instance.getUrl(), serviceName);
////                        allMetrics.put(instance.getUrl(), metrics);
////                    } catch (Exception e) {
////                        // Skip failed metrics
////                    }
////                }
////            });
////        });
////
////        response.put("metrics", allMetrics);
////        response.put("totalInstances", allMetrics.size());
////        response.put("cacheInfo", getCacheInfo());
////
////        return ResponseEntity.ok(response);
////    }
//
    @GetMapping("/{serviceName:.+}")
    public ResponseEntity<Map<String, Object>> getServiceMetrics(@PathVariable("serviceName") String serviceName) {
        try {
            Service service = serviceRegistry.getService(serviceName);
            if (service == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Get pod names for this service
            List<String> podNames = new ArrayList<>();
            service.getInstances().forEach(instance -> {
                podNames.add(instance.getInstanceName());
            });
            
            // Fetch metrics using the corrected PrometheusMetricsService
            Map<String, InstanceMetrics> metricsData = prometheusMetricsService.fetchMetricsForPods(podNames);
            Map<String, Object> metrics = new HashMap<>(metricsData);
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to fetch metrics: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
//
//    @PostMapping("/cache/clear")
//    public ResponseEntity<Map<String, String>> clearCache() {
//        prometheusMetricsService.clearAllCache();
//
//        Map<String, String> response = new HashMap<>();
//        response.put("message", "All metrics cache cleared successfully");
//        response.put("timestamp", java.time.LocalDateTime.now().toString());
//
//        return ResponseEntity.ok(response);
//    }
//
//    @PostMapping("/cache/clear/{serviceUrl:.+}")
//    public ResponseEntity<Map<String, String>> clearCacheForService(@PathVariable("serviceUrl") String serviceUrl) {
//        prometheusMetricsService.clearCacheForService(serviceUrl);
//
//        Map<String, String> response = new HashMap<>();
//        response.put("message", "Cache cleared for service: " + serviceUrl);
//        response.put("timestamp", java.time.LocalDateTime.now().toString());
//
//        return ResponseEntity.ok(response);
//    }
//
//    @GetMapping("/cache/info")
//    public ResponseEntity<Map<String, Object>> getCacheInfo() {
//        Map<String, Object> cacheInfo = new HashMap<>();
//
//        // Get Redis cache statistics
//        try {
//            Long totalKeys = redisTemplate.execute((RedisCallback<Long>) DefaultedRedisConnection::dbSize);
//
//            cacheInfo.put("redisConnected", true);
//            cacheInfo.put("totalCachedKeys", totalKeys);
//            cacheInfo.put("cacheType", "Redis");
//
//        } catch (Exception e) {
//            cacheInfo.put("redisConnected", false);
//            cacheInfo.put("error", e.getMessage());
//        }
//
//        return ResponseEntity.ok(cacheInfo);
//    }
}
