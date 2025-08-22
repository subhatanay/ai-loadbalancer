package com.bits.loadbalancer.utils;

import java.util.Map;

public class RewardCalculator {
    public static double computeReward(Map<String,Object> before, Map<String,Object> after,
                                       String startTime, String endTime) {
        double latencyBefore = (double) before.getOrDefault("avg_response_time_ms", 0.0);
        double latencyAfter  = (double) after .getOrDefault("avg_response_time_ms", 0.0);
        double errorBefore   = (double) before.getOrDefault("error_rate_percent", 0.0);
        double errorAfter    = (double) after .getOrDefault("error_rate_percent", 0.0);

        double deltaLat = latencyAfter - latencyBefore;
        double deltaErr = errorAfter - errorBefore;

        // Negative reward for increased latency and errors
        return -1.0 * (deltaLat/1000.0) -5.0 * (deltaErr/100.0);
    }
}
