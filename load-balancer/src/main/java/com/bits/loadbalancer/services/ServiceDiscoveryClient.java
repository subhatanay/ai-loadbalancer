package com.bits.loadbalancer.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.bits.commomutil.models.ServiceInfo;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ServiceDiscoveryClient {
    Logger logger = LoggerFactory.getLogger(ServiceDiscoveryClient.class);
    private final StringRedisTemplate stringRedisTemplate;
    private final Map<String, List<ServiceInfo>> serviceMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    @Getter
    private long lastRefreshTime;

    public ServiceDiscoveryClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Scheduled(fixedRate = 15000) // Refresh every 15 seconds
    public void refreshServices() {
        try {
            logger.info("Starting service discovery refresh...");
            Set<String> keys = stringRedisTemplate.keys("service:*");
            if (keys == null || keys.isEmpty()) {
                logger.info("No service keys found in Redis");
                return;
            }
            logger.info("Found {} service keys in Redis", keys.size());

            Map<String, List<ServiceInfo>> newServiceMap = new ConcurrentHashMap<>();

            for (String key : keys) {
                try {
                    logger.info("Processing service key: {}", key);
                    // Use StringRedisTemplate to get raw JSON string without deserialization issues
                    String jsonValue = stringRedisTemplate.opsForValue().get(key);
                    if (jsonValue != null) {
                        logger.info("Got JSON value for key {}: {}", key, jsonValue.substring(0, Math.min(100, jsonValue.length())));
                        ServiceInfo info = parseServiceInfoFromJson(jsonValue);
                        if (info != null) {
                            newServiceMap.computeIfAbsent(info.getServiceName(),
                                    k -> new ArrayList<>()).add(info);
                            logger.info("Successfully parsed ServiceInfo for {}", info.getServiceName());
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to process service key {}: {}", key, e.getMessage(), e);
                }
            }
            serviceMap.clear();
            serviceMap.putAll(newServiceMap);
            lastRefreshTime = System.currentTimeMillis();
            logger.info("\uD83D\uDD04 Refreshed {} service groups", serviceMap.size());
        } catch (Exception e) {
            logger.error("Error during service discovery refresh: {}", e.getMessage(), e);
        }
    }

    public List<ServiceInfo> getServices(String serviceName) {
        return serviceMap.getOrDefault(serviceName, Collections.emptyList());
    }

    public Map<String, List<ServiceInfo>> getAllServices() {
        return new ConcurrentHashMap<>(serviceMap);
    }

    private ServiceInfo parseServiceInfoFromJson(String jsonValue) {
        try {
            return objectMapper.readValue(jsonValue, ServiceInfo.class);
        } catch (Exception e) {
            logger.error("Failed to parse ServiceInfo from JSON: {} - {}", jsonValue, e.getMessage());
            return null;
        }
    }



    private java.time.LocalDateTime parseLastHealthCheck(Object lastHealthCheckObj) {
        if (lastHealthCheckObj == null) {
            return null;
        }
        
        try {
            if (lastHealthCheckObj instanceof String) {
                // Handle string format: "2025-08-05T16:16:31.036394513"
                return java.time.LocalDateTime.parse((String) lastHealthCheckObj);
            } else if (lastHealthCheckObj instanceof java.util.List) {
                // Handle array format: [2025,8,5,16,16,10,634832004]
                @SuppressWarnings("unchecked")
                java.util.List<Number> dateArray = (java.util.List<Number>) lastHealthCheckObj;
                
                if (dateArray.size() >= 6) {
                    int year = dateArray.get(0).intValue();
                    int month = dateArray.get(1).intValue();
                    int day = dateArray.get(2).intValue();
                    int hour = dateArray.get(3).intValue();
                    int minute = dateArray.get(4).intValue();
                    int second = dateArray.get(5).intValue();
                    int nano = dateArray.size() > 6 ? dateArray.get(6).intValue() : 0;
                    
                    return java.time.LocalDateTime.of(year, month, day, hour, minute, second, nano);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse lastHealthCheck: {} - {}", lastHealthCheckObj, e.getMessage());
        }
        
        return null;
    }

}
