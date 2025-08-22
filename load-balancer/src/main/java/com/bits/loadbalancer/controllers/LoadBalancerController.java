package com.bits.loadbalancer.controllers;

import com.bits.commomutil.models.ServiceInfo;
import com.bits.loadbalancer.services.RoutingStrategyAlgorithm;
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

    @RequestMapping(value = "/{serviceName}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<?> proxyRequest(
            @PathVariable("serviceName") String serviceName,
            HttpServletRequest request,
            @RequestBody(required = false) Object body) {
        
        logger.info("Proxying request to service: {}", serviceName);

        // Get next healthy instance
        ServiceInfo targetInstance = routingStrategyAlgorithm.getLoadbalancer().geNextServiceInstance(serviceName);

        if (targetInstance == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "No healthy instances available for service: " + serviceName);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
        }
        
        // CRITICAL: Set chosen instance BEFORE processing for RL experience logging
        request.setAttribute("chosenPodInstance", targetInstance.getInstanceName());
        logger.info("Selected instance {} for service {}", targetInstance.getInstanceName(), serviceName);
        
        try {
            // Build URI properly to avoid encoding issues
            URI targetUri = buildTargetUri(request, serviceName, targetInstance);
            logger.info("Forwarding request to: {}", targetUri);
            
            // Copy headers
            HttpHeaders headers = copyHeaders(request);
            
            // Create HTTP entity
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);
            
            // Forward the request with proper URI
            HttpMethod method = HttpMethod.valueOf(request.getMethod());
            logger.info("******************** HTTP REQUEST {}, HTTP Method: {} *********************", request, method);

            // Use String.class to avoid chunked encoding issues with Object.class
            ResponseEntity<String> response = restTemplate.exchange(
                    targetUri, method, entity, String.class);

            logger.info("Request forwarded successfully to {} - Status: {}",
                    targetUri, response.getStatusCode());

            // Parse response body as JSON if possible, otherwise return as string
            Object responseBody = parseResponseBody(response.getBody());
            
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(responseBody);

        } catch (Exception e) {
            logger.error("Error forwarding request: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to forward request: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
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