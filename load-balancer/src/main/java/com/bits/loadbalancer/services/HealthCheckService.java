package com.bits.loadbalancer.services;

import com.bits.loadbalancer.model.Service;
import com.bits.commomutil.models.ServiceInfo;
import com.bits.loadbalancer.dao.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import java.time.LocalDateTime;

@Component
public class HealthCheckService {
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckService.class);

    @Autowired
    private ServiceRegistry serviceRegistry;

    private final RestTemplate restTemplate = new RestTemplate();

    @Scheduled(fixedRate = 5000) // Check every 5 seconds
    public void performHealthChecks() {
        logger.debug("Starting health checks for all services");

        for (Service service : serviceRegistry.getAllServices()) {
            for (ServiceInfo instance : service.getInstances()) {
                checkInstanceHealth(service.getName(), instance);
            }
        }
    }

    private void checkInstanceHealth(String serviceName, ServiceInfo instance) {
        try {
            long startTime = System.currentTimeMillis();

            // Perform health check
            restTemplate.getForObject(instance.getHealthUrl(), String.class);

            long responseTime = System.currentTimeMillis() - startTime;

            // Update instance health
            instance.setHealthy(true);
            instance.setLastHealthCheck(LocalDateTime.now());
            instance.setResponseTime(responseTime);

            logger.debug("Health check passed for {} - {} ({}ms)",
                    serviceName, instance.getUrl(), responseTime);

        } catch (RestClientException e) {
            // Mark as unhealthy
            instance.setHealthy(false);
            instance.setLastHealthCheck(LocalDateTime.now());

            logger.warn("Health check failed for {} - {}: {}",
                    serviceName, instance.getUrl(), e.getMessage());
        }
    }

    public void forceHealthCheck(String serviceName, String instanceUrl) {
        Service service = serviceRegistry.getService(serviceName);
        if (service != null) {
            service.getInstances().stream()
                    .filter(instance -> instance.getUrl().equals(instanceUrl))
                    .findFirst()
                    .ifPresent(instance -> checkInstanceHealth(serviceName, instance));
        }
    }
}