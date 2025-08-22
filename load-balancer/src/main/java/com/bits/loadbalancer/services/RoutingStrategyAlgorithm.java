package com.bits.loadbalancer.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Component
public class RoutingStrategyAlgorithm {

    @Getter
    @Value("${loadbalancer.routing-strategy:round-robin}")
    private String routingStrategy;

    @Autowired
    RoundRobinBasedLoadbalancer roundRobinLoadBalancer;

//    @Autowired
    ConnectionAwareLoadBalancer connectionAwareLoadBalancer;

    @Autowired
    RLBasedLoadbalancer rlBasedLoadbalancer;

    @Autowired
    RLApiLoadBalancer rlApiLoadBalancer;


    public Loadbalancer getLoadbalancer() {
        return switch (routingStrategy) {
            case "connection-aware" -> connectionAwareLoadBalancer;
            case "rl-based" -> rlApiLoadBalancer;  // Use new RL API-based load balancer
            case "rl-static" -> rlBasedLoadbalancer;  // Keep old static Q-table for comparison
            default -> roundRobinLoadBalancer;
        };
    }

}
