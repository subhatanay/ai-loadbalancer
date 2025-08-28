package com.bits.loadbalancer.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "collector")
public class CollectorProperties {
    private static final Logger log = LoggerFactory.getLogger(CollectorProperties.class);
    private boolean enabled = false;  // Default to disabled
    private String endpointUrl;
    private double rewardScale;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public double getRewardScale() { return rewardScale; }
    public void setRewardScale(double rewardScale) { this.rewardScale = rewardScale; }
    
    @PostConstruct
    public void logConfiguration() {
        log.info("CollectorProperties initialized: enabled={}, endpointUrl={}, rewardScale={}", 
                enabled, endpointUrl, rewardScale);
        log.info("Environment variable RL_COLLECTOR_ENABLED: {}", System.getenv("RL_COLLECTOR_ENABLED"));
    }
}
