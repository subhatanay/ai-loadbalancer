package com.bits.loadbalancer.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "collector")
public class CollectorProperties {
    private String endpointUrl;
    private double rewardScale;

    public String getEndpointUrl() { return endpointUrl; }
    public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }

    public double getRewardScale() { return rewardScale; }
    public void setRewardScale(double rewardScale) { this.rewardScale = rewardScale; }
}
