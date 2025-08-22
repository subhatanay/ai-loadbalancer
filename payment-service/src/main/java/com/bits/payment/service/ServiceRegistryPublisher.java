package com.bits.payment.service;

import com.bits.commomutil.models.ServiceInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ServiceRegistryPublisher {

    private final Logger logger = LoggerFactory.getLogger(ServiceRegistryPublisher.class);

    private final RedisTemplate<String, ServiceInfo> redisTemplateForServiceRegistration;
    private ServiceInfo serviceInfo;
    private String serviceKey;

    @Value("${service.name}")
    private String serviceName;

    @Value("${server.port}")
    private int servicePort;

    @Value("${service.host:localhost}")
    private String serviceHost;

    @Value("${service.registration.ttl-seconds:30}")
    private long ttlSeconds;

    @Value("${POD_NAME:unknown-pod}")
    private String podName;

    @Value("${POD_IP:127.0.0.1}")
    private String podIp;

    public ServiceRegistryPublisher(RedisTemplate<String, ServiceInfo> redisTemplateForServiceRegistration) {
        this.redisTemplateForServiceRegistration = redisTemplateForServiceRegistration;
    }

    @PostConstruct
    public void registerService() {
        try {
            String url = "http://" + podIp + ":" + servicePort;
            String healthUrl = url + "/actuator/health";

            serviceInfo = new ServiceInfo(serviceName, url, healthUrl, podName);
            serviceKey = "service:" + serviceName + ":" + podName;

            redisTemplateForServiceRegistration.opsForValue().set(serviceKey, serviceInfo, ttlSeconds, TimeUnit.SECONDS);
            logger.info("✅ Service registered: {}", serviceKey);

        } catch (Exception e) {
            logger.error("❌ Service registration failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedRate = 20000) // Refresh every 20 seconds
    public void refreshRegistration() {
        if (serviceInfo != null) {
            // Update instance health
            serviceInfo.setHealthy(true);
            serviceInfo.setLastHealthCheck(LocalDateTime.now());
            redisTemplateForServiceRegistration.opsForValue().set(serviceKey, serviceInfo, ttlSeconds, TimeUnit.SECONDS);
            logger.info("♻️ Service heartbeat: {}", serviceKey);
        }
    }

    @PreDestroy 
    public void deregisterService() {
        if (serviceKey != null) {
            redisTemplateForServiceRegistration.delete(serviceKey);
            logger.info("\uD83D\uDEAB Service deregistered: {}", serviceKey);
        }
    }
}
