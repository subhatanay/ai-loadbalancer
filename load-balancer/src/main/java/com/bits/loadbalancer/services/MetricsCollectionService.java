package com.bits.loadbalancer.services;

import com.bits.loadbalancer.dao.ServiceRegistry;
import com.bits.loadbalancer.model.Service;
import com.bits.commomutil.models.ServiceInfo;
import com.bits.loadbalancer.model.ServiceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

//@Component
public class MetricsCollectionService {
//    private static final Logger logger = LoggerFactory.getLogger(MetricsCollectionService.class);
//
////    @Autowired
//    private ServiceRegistry serviceRegistry;
//
////    @Autowired
//    private PrometheusMetricsService prometheusMetricsService;
//
//    @Value("${loadbalancer.metrics.collection-interval:10000}")
//    private long collectionIntervalMs;
//
//    @Scheduled(fixedRateString = "${loadbalancer.metrics.collection-interval:10000}")
//    public void collectAndCacheMetrics() {
//        logger.debug("Starting metrics collection and caching cycle");
//
//        long startTime = System.currentTimeMillis();
//        int totalInstances = 0;
//        int successfulCollections = 0;
//
//        for (Service service : serviceRegistry.getAllServices()) {
//            for (ServiceInfo instance : service.getInstances()) {
//                if (instance.isHealthy()) {
//                    totalInstances++;
//                    try {
//                        String serviceName = service.getName();
//                        ServiceMetrics metrics = prometheusMetricsService.getServiceMetrics(
//                                instance.getUrl(), serviceName);
//
//                        if (metrics != null && metrics.isHealthy()) {
//                            successfulCollections++;
//                            logger.debug("Successfully collected metrics for url {}: connections={}/{}, load={} %",
//                                    metrics.getServiceUrl(),
//                                    metrics.getActiveConnections(),
//                                    metrics.getMaxConnections(),
//                                    metrics.getConnectionLoadScore());
//                        }
//
//                    } catch (Exception e) {
//                        logger.warn("Failed to collect metrics for {}: {}", instance.getUrl(), e.getMessage());
//                    }
//                }
//            }
//        }
//
//        long duration = System.currentTimeMillis() - startTime;
//        logger.info("Metrics collection completed: {}/{} instances successful in {}ms",
//                successfulCollections, totalInstances, duration);
//    }
//
//    @Scheduled(fixedRate = 300000) // Every 5 minutes
//    public void logMetricsSummary() {
//        logger.info("=== METRICS SUMMARY ===");
//
//        for (Service service : serviceRegistry.getAllServices()) {
//            logger.info("Service: {}", service.getName());
//
//            for (ServiceInfo instance : service.getInstances()) {
//                if (instance.isHealthy()) {
//                    try {
//                        String serviceName = service.getName();
//                        ServiceMetrics metrics = prometheusMetricsService.getServiceMetrics(
//                                instance.getUrl(), serviceName);
//
//                        logger.info("  Instance: {} - {}", instance.getUrl(), metrics);
//
//                    } catch (Exception e) {
//                        logger.info("  Instance: {} - ERROR: {}", instance.getUrl(), e.getMessage());
//                    }
//                } else {
//                    logger.info("  Instance: {} - UNHEALTHY", instance.getUrl());
//                }
//            }
//        }
//
//        logger.info("=== END SUMMARY ===");
//    }
}
