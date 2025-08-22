package com.bits.cartservice.client;

import com.bits.cartservice.dto.InventoryItemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceClient {

    private final RestTemplate restTemplate;

    @Value("${services.ai-loadbalancer.url:http://ai-loadbalancer-service:8080}")
    private String loadBalancerUrl;

    public Optional<InventoryItemDto> getProductInventory(String productSku) {
        return getProductInventory(productSku, null);
    }
    
    public Optional<InventoryItemDto> getProductInventory(String productSku, String jwtToken) {
        try {
            log.debug("Fetching inventory for product: {} through load balancer", productSku);
            
            String url = loadBalancerUrl + "/proxy/inventory-service/api/inventory/product/" + productSku;
            
            // Create headers with JWT token
            HttpHeaders headers = new HttpHeaders();
            
            // Get JWT token from current request context if not provided
            String token = jwtToken;
            if (token == null) {
                token = extractJwtTokenFromCurrentRequest();
            }
            
            if (token != null) {
                headers.set("Authorization", "Bearer " + token);
                log.debug("Added Authorization header for inventory service call");
            } else {
                log.warn("No JWT token available for inventory service call");
            }
            
            // Add other necessary headers
            headers.set("Content-Type", "application/json");
            
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<InventoryItemDto> response = restTemplate.exchange(url, HttpMethod.GET, entity, InventoryItemDto.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Successfully fetched inventory for product: {}", productSku);
                return Optional.of(response.getBody());
            } else {
                log.warn("Product not found in inventory: {}", productSku);
                return Optional.empty();
            }
            
        } catch (RestClientException e) {
            log.error("Failed to fetch inventory for product: {}", productSku, e);
            return Optional.empty();
        }
    }

    public boolean isProductAvailable(String productSku, Integer requestedQuantity) {
        Optional<InventoryItemDto> inventory = getProductInventory(productSku);
        
        if (inventory.isEmpty()) {
            log.warn("Product not found in inventory: {}", productSku);
            return false;
        }
        
        InventoryItemDto item = inventory.get();
        boolean available = item.getAvailableQuantity() != null && 
                           item.getAvailableQuantity() >= requestedQuantity;
        
        log.debug("Product {} availability check: requested={}, available={}, result={}", 
                 productSku, requestedQuantity, item.getAvailableQuantity(), available);
        
        return available;
    }
    
    public boolean isProductAvailable(String productSku, Integer requestedQuantity, String jwtToken) {
        Optional<InventoryItemDto> inventory = getProductInventory(productSku, jwtToken);
        
        if (inventory.isEmpty()) {
            log.warn("Product not found in inventory: {}", productSku);
            return false;
        }
        
        InventoryItemDto item = inventory.get();
        boolean available = item.getAvailableQuantity() != null && 
                           item.getAvailableQuantity() >= requestedQuantity;
        
        log.debug("Product {} availability check: requested={}, available={}, result={}", 
                 productSku, requestedQuantity, item.getAvailableQuantity(), available);
        
        return available;
    }
    
    /**
     * Extract JWT token from the current HTTP request context
     */
    private String extractJwtTokenFromCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7); // Remove "Bearer " prefix
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract JWT token from current request: {}", e.getMessage());
        }
        return null;
    }
}
