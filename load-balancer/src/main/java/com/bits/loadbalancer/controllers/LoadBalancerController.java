package com.bits.loadbalancer.controllers;

import com.bits.commomutil.models.ServiceInfo;
import com.bits.commomutil.tracing.TraceUtils;
import com.bits.loadbalancer.metrics.LoadBalancerMetrics;
import com.bits.loadbalancer.services.BenchmarkController;
import com.bits.loadbalancer.services.RoutingStrategyAlgorithm;
import com.bits.loadbalancer.services.LeastConnectionsLoadBalancer;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/proxy")
public class LoadBalancerController {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerController.class);

    @Autowired
    private RoutingStrategyAlgorithm routingStrategyAlgorithm;

    @Autowired
    private RestTemplate restTemplate ;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private LoadBalancerMetrics loadBalancerMetrics;
    
    @Autowired(required = false)
    private BenchmarkController benchmarkController;
    
    @Autowired(required = false)
    private LeastConnectionsLoadBalancer leastConnectionsLoadBalancer;

    @RequestMapping(value = "/{serviceName}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<?> proxyRequest(
            @PathVariable("serviceName") String serviceName,
            HttpServletRequest request,
            @RequestBody(required = false) Object body) {
        
        // Handle trace ID for request tracking
        String traceId = TraceUtils.getOrGenerateTraceId(request.getHeader(TraceUtils.TRACE_ID_HEADER));
        TraceUtils.setTraceId(traceId);
        
        logger.info("Proxying request to service: {} [traceId={}]", serviceName, traceId);

        // Start metrics collection for proxy request
        Timer.Sample proxyRequestSample = loadBalancerMetrics.startProxyRequest();
        long startTime = System.currentTimeMillis();
        ServiceInfo targetInstance = null;
        
        try {
            // Get next healthy instance
            long decisionStartTime = System.currentTimeMillis();
            targetInstance = routingStrategyAlgorithm.getLoadbalancer().geNextServiceInstance(serviceName);
            long decisionEndTime = System.currentTimeMillis();
            logger.info("Routing decision usung {} for service {} took: {}ms", routingStrategyAlgorithm.getRoutingStrategy(), serviceName, (decisionEndTime - decisionStartTime));

            if (targetInstance == null) {
                long responseTime = System.currentTimeMillis() - startTime;
                loadBalancerMetrics.recordProxyError(serviceName, "no_healthy_instances", "503");
                
                // Record benchmark metrics for no healthy instances error
                if (benchmarkController != null) {
                    benchmarkController.recordRequest(responseTime, true, request.getRequestURI()); // true = error occurred
                }
                
                Map<String, String> error = new HashMap<>();
                error.put("error", "No healthy instances available for service: " + serviceName);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
            }
            
            // CRITICAL: Set chosen instance BEFORE processing for RL experience logging
            request.setAttribute("chosenPodInstance", targetInstance.getInstanceName());
            logger.debug("Selected instance {} for service {}", targetInstance.getInstanceName(), serviceName);
            
            // Record pod request metrics
            loadBalancerMetrics.recordPodRequest(serviceName, targetInstance.getInstanceName());
            
            // Build URI properly to avoid encoding issues
            URI targetUri = buildTargetUri(request, serviceName, targetInstance);
            logger.debug("Forwarding request to: {}", targetUri);
            
            // Copy headers and add trace ID
            HttpHeaders headers = copyHeaders(request);
            headers.set(TraceUtils.TRACE_ID_HEADER, traceId);
            
            // Create HTTP entity
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            
            // Forward the request with proper URI
            HttpMethod method = HttpMethod.valueOf(request.getMethod());
            logger.debug("******************** HTTP REQUEST {}, HTTP Method: {} *********************", request, method);

            // Use String.class to avoid chunked encoding issues with Object.class
            long forwardStartTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.exchange(
                    targetUri, method, entity, String.class);
            long forwardEndTime = System.currentTimeMillis();
            logger.info("Request forwarding to {} took: {}ms", targetUri, (forwardEndTime - forwardStartTime));

            long responseTime = System.currentTimeMillis() - startTime;
            
            logger.info("Request forwarded successfully to {} - Status: {}, ResponseTime: {}ms",
                    targetUri, response.getStatusCode(), responseTime);

            // Record pod response metrics
            loadBalancerMetrics.recordPodResponse(serviceName, targetInstance.getInstanceName(), 
                    responseTime, response.getStatusCode().value());

            // Provide feedback to RL agent for learning
            provideFeedbackToRLAgent(serviceName, targetInstance.getInstanceName(), 
                                   responseTime, response.getStatusCode().value());

            // Stop metrics collection
            loadBalancerMetrics.recordProxyRequest(proxyRequestSample, serviceName, 
                    String.valueOf(response.getStatusCode().value()));

            // Record benchmark metrics if in benchmark mode
            if (benchmarkController != null) {
                benchmarkController.recordRequest(responseTime, !response.getStatusCode().is2xxSuccessful(), request.getRequestURI());
            }
            
            // Decrement connection count for least connections algorithm
            if (leastConnectionsLoadBalancer != null && "least-connections".equals(routingStrategyAlgorithm.getRoutingStrategy())) {
                leastConnectionsLoadBalancer.decrementConnections(targetInstance.getUrl());
            }

            // Parse response body as JSON if possible, otherwise return as string
            Object responseBody = parseResponseBody(response.getBody());
            
            // Add trace ID to response headers
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.putAll(response.getHeaders());
            responseHeaders.set(TraceUtils.TRACE_ID_HEADER, traceId);
            
            return ResponseEntity.status(response.getStatusCode())
                    .headers(responseHeaders)
                    .body(responseBody);

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            logger.error("Error forwarding request: {}", e.getMessage(), e);
            
            // Record pod response error metrics only if we have a target instance
            if (targetInstance != null) {
                loadBalancerMetrics.recordPodResponse(serviceName, targetInstance.getInstanceName(), 
                        responseTime, 502);
                
                // Provide negative feedback to RL agent for failed requests
                provideFeedbackToRLAgent(serviceName, targetInstance.getInstanceName(), 
                                       responseTime, 502); // BAD_GATEWAY
            }
            
            // Record proxy error metrics
            loadBalancerMetrics.recordProxyError(serviceName, "request_forwarding_failed", "502");
            
            // Record benchmark metrics for errors
            if (benchmarkController != null) {
                benchmarkController.recordRequest(responseTime, true, request.getRequestURI()); // true = error occurred
            }
            
            // Decrement connection count for least connections algorithm even on error
            if (targetInstance != null && leastConnectionsLoadBalancer != null && 
                "least-connections".equals(routingStrategyAlgorithm.getRoutingStrategy())) {
                leastConnectionsLoadBalancer.decrementConnections(targetInstance.getUrl());
            }
            
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to forward request: " + e.getMessage());
            
            // Add trace ID to error response
            HttpHeaders errorHeaders = new HttpHeaders();
            errorHeaders.set(TraceUtils.TRACE_ID_HEADER, traceId);
            
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .headers(errorHeaders)
                    .body(error);
        }
    }
    
    /**
     * Provide feedback to RL agent about routing outcome
     */
    private void provideFeedbackToRLAgent(String serviceName, String selectedPod, long responseTimeMs, int statusCode) {
        try {
            // Check if using RL-based load balancer
            if (routingStrategyAlgorithm.getLoadbalancer() instanceof com.bits.loadbalancer.services.RLApiLoadBalancer) {
                com.bits.loadbalancer.services.RLApiLoadBalancer rlLoadBalancer = 
                    (com.bits.loadbalancer.services.RLApiLoadBalancer) routingStrategyAlgorithm.getLoadbalancer();
                
                boolean errorOccurred = statusCode >= 400;
                rlLoadBalancer.provideFeedback(serviceName, selectedPod, responseTimeMs, statusCode, errorOccurred);
                
                // Record feedback metrics
                loadBalancerMetrics.recordFeedbackSent(serviceName, selectedPod, responseTimeMs);
                
                logger.debug("RL feedback provided: {} -> {} ({}ms, status: {}, error: {})", 
                           serviceName, selectedPod, responseTimeMs, statusCode, errorOccurred);
            }
        } catch (Exception e) {
            // Record feedback failure metrics
            loadBalancerMetrics.recordFeedbackFailed(serviceName, selectedPod, e.getClass().getSimpleName());
            logger.debug("Failed to provide RL feedback (non-critical): {}", e.getMessage());
        }
    }
    
private URI buildTargetUri(HttpServletRequest request, String serviceName, ServiceInfo targetInstance) {
    try {
        // Extract the path after the service name
        String requestPath = extractPathAfterServiceName(request.getRequestURI(), serviceName);
        
        // Ensure requestPath starts with /
        if (!requestPath.startsWith("/") && !requestPath.isEmpty()) {
            requestPath = "/" + requestPath;
        }
        
        // Build URI using UriComponentsBuilder to handle encoding properly
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl(targetInstance.getUrl())
                .path(requestPath);
        
        // Add query parameters if present
        if (request.getQueryString() != null && !request.getQueryString().trim().isEmpty()) {
            // Parse and add query parameters safely
            String queryString = request.getQueryString();
            String[] params = queryString.split("&");
            
            for (String param : params) {
                if (param.contains("=")) {
                    String[] keyValue = param.split("=", 2);
                    String key = keyValue[0].trim();
                    String value = keyValue.length > 1 ? keyValue[1].trim() : "";
                    
                    if (!key.isEmpty()) {
                        uriBuilder.queryParam(key, value);
                    }
                }
            }
        }
        
        return uriBuilder.build().toUri();
        
    } catch (Exception e) {
        logger.error("Error building target URI: {}", e.getMessage(), e);
        throw new IllegalArgumentException("Invalid URL construction", e);
    }
}

private String extractPathAfterServiceName(String requestUri, String serviceName) {
    String prefix = "/proxy/" + serviceName;
    
    if (requestUri.startsWith(prefix)) {
        String path = requestUri.substring(prefix.length());
        
        // Handle empty path
        if (path.isEmpty()) {
            return "/";
        }
        
        // Ensure path starts with /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        
        return path;
    }
    
    // Default to root path
    return "/";
}

private HttpHeaders copyHeaders(HttpServletRequest request) {
    HttpHeaders headers = new HttpHeaders();
    
    Enumeration<String> headerNames = request.getHeaderNames();
    while (headerNames.hasMoreElements()) {
        String headerName = headerNames.nextElement();
        String headerValue = request.getHeader(headerName);
        
        // Skip problematic headers that might cause issues
        if (shouldSkipHeader(headerName)) {
            continue;
        }
        
        // Validate header value doesn't contain invalid characters
        if (headerValue != null && isValidHeaderValue(headerValue)) {
            headers.add(headerName, headerValue);
        }
    }
    
    return headers;
}

private boolean shouldSkipHeader(String headerName) {
    // Skip headers that should not be forwarded and can cause response corruption
    return headerName.equalsIgnoreCase("host") ||
           headerName.equalsIgnoreCase("content-length") ||
           headerName.equalsIgnoreCase("transfer-encoding") ||
           headerName.equalsIgnoreCase("content-encoding") ||
           headerName.equalsIgnoreCase("accept-encoding") ||
           headerName.equalsIgnoreCase("connection") ||
           headerName.equalsIgnoreCase("upgrade");
}

private boolean isValidHeaderValue(String headerValue) {
    // Check for invalid characters that might cause HTTP parsing issues
    return !headerValue.contains("\r") && 
           !headerValue.contains("\n") && 
           !headerValue.contains("\0");
}

    
    /**
     * Parse response body as JSON if possible, otherwise return as string
     */
    private Object parseResponseBody(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return responseBody;
        }

        try {
            // Try to parse as JSON object
            return objectMapper.readValue(responseBody, Object.class);
        } catch (JsonProcessingException e) {
            // If not valid JSON, return as string
            return responseBody;
        }
    }
    
    /**
     * Parse error response body as JSON if possible, otherwise return as string
     */
    private Object parseErrorResponse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            Map<String, String> fallback = new HashMap<>();
            fallback.put("error", "Empty error response");
            return fallback;
        }

        try {
            // Try to parse as JSON object
            return objectMapper.readValue(responseBody, Object.class);
        } catch (JsonProcessingException e) {
            // If not valid JSON, check if it looks like a JSON string that needs unescaping
            String trimmed = responseBody.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                try {
                    // Remove outer quotes and unescape
                    String unescaped = objectMapper.readValue(trimmed, String.class);
                    // Try to parse the unescaped string as JSON
                    return objectMapper.readValue(unescaped, Object.class);
                } catch (JsonProcessingException ex) {
                    // Still not JSON, return the unescaped string
                    try {
                        String unescapedFallback = objectMapper.readValue(trimmed, String.class);
                        return unescapedFallback;
                    } catch (JsonProcessingException ex2) {
                        // Return original trimmed string if unescaping fails
                        return trimmed;
                    }
                }
            }
            
            // Return as plain string if not JSON
            logger.debug("Response body is not valid JSON, returning as string: {}", responseBody);
            Map<String, String> fallback = new HashMap<>();
            fallback.put("error", responseBody);
            fallback.put("errorType", "NON_JSON_RESPONSE");
            return fallback;
        }
    }
}