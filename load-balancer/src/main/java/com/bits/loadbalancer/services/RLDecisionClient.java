package com.bits.loadbalancer.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

/**
 * Client for communicating with RL Decision API
 * Handles intelligent routing decisions using reinforcement learning
 */
@Service
public class RLDecisionClient {
    
    private static final Logger logger = LoggerFactory.getLogger(RLDecisionClient.class);
    
    private final WebClient webClient;
    private final String rlApiBaseUrl;
    
    public RLDecisionClient(@Value("${rl.decision.api.url:http://localhost:8088}") String rlApiBaseUrl) {
        this.rlApiBaseUrl = rlApiBaseUrl;
        this.webClient = WebClient.builder()
                .baseUrl(rlApiBaseUrl)
                .build();
        
        logger.info("RL Decision Client initialized with URL: {}", rlApiBaseUrl);
    }
    
    public String getApiUrl() {
        return rlApiBaseUrl;
    }
    
    /**
     * Request routing decision from RL agent
     */
    public Mono<RoutingDecision> getRoutingDecision(String serviceName, String requestPath) {
        RoutingRequest request = new RoutingRequest(serviceName, requestPath);
        
        logger.debug("Requesting RL decision for service: {}, path: {}, URL: {}/decide", serviceName, requestPath, rlApiBaseUrl);
        
        return webClient.post()
                .uri("/decide")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RoutingDecision.class)
                .timeout(Duration.ofMillis(2000)) // Fast timeout for low latency
                .retryWhen(Retry.fixedDelay(1, Duration.ofMillis(10)))
                .doOnSuccess(decision -> logger.info("RL decision SUCCESS: {} -> {} ({}ms)", 
                    serviceName, decision.selectedPod, decision.decisionTimeMs))
                .doOnError(error -> {
                    logger.error("RL decision FAILED for service: {}, URL: {}/decide, Error: {}, Type: {}", 
                        serviceName, rlApiBaseUrl, error.getMessage(), error.getClass().getSimpleName());
                    if (error.getCause() != null) {
                        logger.error("Root cause: {}", error.getCause().getMessage());
                    }
                });
    }
    
    /**
     * Provide feedback to RL agent for continuous learning
     */
    public void provideFeedback(String serviceName, String selectedPod, double responseTimeMs, 
                               int statusCode, boolean errorOccurred) {
        logger.debug("Sending feedback for service: {}, pod: {}, responseTime: {}ms, status: {}, URL: {}/feedback", 
            serviceName, selectedPod, responseTimeMs, statusCode, rlApiBaseUrl);
            
        webClient.post()
                .uri("/feedback")
                .bodyValue(new FeedbackRequest(serviceName, selectedPod, responseTimeMs, statusCode, errorOccurred))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(2000))
                .subscribe(
                    result -> logger.debug("Feedback SUCCESS: {} -> {} ({}ms)", serviceName, selectedPod, responseTimeMs),
                    error -> {
                        logger.warn("Feedback FAILED for service: {}, URL: {}/feedback, Error: {}, Type: {}", 
                            serviceName, rlApiBaseUrl, error.getMessage(), error.getClass().getSimpleName());
                        if (error.getCause() != null) {
                            logger.warn("Feedback root cause: {}", error.getCause().getMessage());
                        }
                    }
                );
    }
    
    /**
     * Check if RL API is healthy and ready
     */
    public Mono<Boolean> isHealthy() {
        logger.debug("Checking RL API health at URL: {}/health", rlApiBaseUrl);
        
        return webClient.get()
                .uri("/health")
                .retrieve()
                .bodyToMono(HealthResponse.class)
                .map(health -> {
                    boolean isHealthy = "healthy".equals(health.status) && health.rlAgentReady;
                    logger.info("RL API health check result: status={}, rlAgentReady={}, isHealthy={}, qTableSize={}", 
                        health.status, health.rlAgentReady, isHealthy, health.qTableSize);
                    return isHealthy;
                })
                .timeout(Duration.ofMillis(2000))
                .doOnError(error -> {
                    logger.error("RL API health check FAILED at URL: {}/health, Error: {}, Type: {}", 
                        rlApiBaseUrl, error.getMessage(), error.getClass().getSimpleName());
                    if (error.getCause() != null) {
                        logger.error("Health check root cause: {}", error.getCause().getMessage());
                    }
                })
                .onErrorReturn(false);
    }
    
    /**
     * Get RL agent statistics
     */
    public Mono<RLStats> getStats() {
        logger.debug("Requesting RL agent stats from URL: {}/stats", rlApiBaseUrl);
        
        return webClient.get()
                .uri("/stats")
                .retrieve()
                .bodyToMono(RLStats.class)
                .doOnSuccess(stats -> logger.debug("RL stats SUCCESS: qTableSize={}, episodeCount={}, totalDecisions={}", 
                    stats.qTableSize, stats.episodeCount, stats.totalDecisions))
                .timeout(Duration.ofMillis(2000))
                .doOnError(error -> {
                    logger.warn("RL stats FAILED at URL: {}/stats, Error: {}, Type: {}", 
                        rlApiBaseUrl, error.getMessage(), error.getClass().getSimpleName());
                    if (error.getCause() != null) {
                        logger.warn("Stats root cause: {}", error.getCause().getMessage());
                    }
                })
                .onErrorReturn(new RLStats());
    }
    
    // DTOs
    public static class RoutingRequest {
        @JsonProperty("service_name")
        public String serviceName;
        
        @JsonProperty("request_path")
        public String requestPath;
        
        @JsonProperty("request_method")
        public String requestMethod = "GET";
        
        public RoutingRequest(String serviceName, String requestPath) {
            this.serviceName = serviceName;
            this.requestPath = requestPath;
        }
    }
    
    public static class RoutingDecision {
        @JsonProperty("selected_pod")
        public String selectedPod;
        
        @JsonProperty("confidence")
        public double confidence;
        
        @JsonProperty("decision_type")
        public String decisionType;
        
        @JsonProperty("state_encoded")
        public String stateEncoded;
        
        @JsonProperty("available_pods")
        public List<String> availablePods;
        
        @JsonProperty("decision_time_ms")
        public double decisionTimeMs;
        
        @JsonProperty("timestamp")
        public String timestamp;
    }
    
    public static class FeedbackRequest {
        @JsonProperty("service_name")
        public String serviceName;
        
        @JsonProperty("selected_pod")
        public String selectedPod;
        
        @JsonProperty("response_time_ms")
        public double responseTimeMs;
        
        @JsonProperty("status_code")
        public int statusCode;
        
        @JsonProperty("error_occurred")
        public boolean errorOccurred;
        
        public FeedbackRequest(String serviceName, String selectedPod, double responseTimeMs, 
                             int statusCode, boolean errorOccurred) {
            this.serviceName = serviceName;
            this.selectedPod = selectedPod;
            this.responseTimeMs = responseTimeMs;
            this.statusCode = statusCode;
            this.errorOccurred = errorOccurred;
        }
    }
    
    public static class HealthResponse {
        @JsonProperty("status")
        public String status;
        
        @JsonProperty("rl_agent_ready")
        public boolean rlAgentReady;
        
        @JsonProperty("prometheus_connected")
        public boolean prometheusConnected;
        
        @JsonProperty("q_table_size")
        public int qTableSize;
    }
    
    public static class RLStats {
        @JsonProperty("q_table_size")
        public int qTableSize = 0;
        
        @JsonProperty("current_epsilon")
        public double currentEpsilon = 0.0;
        
        @JsonProperty("episode_count")
        public int episodeCount = 0;
        
        @JsonProperty("total_decisions")
        public int totalDecisions = 0;
        
        @JsonProperty("average_reward")
        public double averageReward = 0.0;
    }
}
