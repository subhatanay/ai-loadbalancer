package com.bits.cartservice.service;

import java.math.BigDecimal;
import java.util.Optional;

import com.bits.cartservice.client.InventoryServiceClient;
import com.bits.cartservice.dto.InventoryItemDto;
import com.bits.cartservice.exception.InvalidProductException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartValidationService {

    private final InventoryServiceClient inventoryServiceClient;

    public void validateProduct(String productId, BigDecimal expectedPrice) {
        log.debug("Validating product: {} with expected price: {}", productId, expectedPrice);

        // Basic validation
        if (productId == null || productId.trim().isEmpty()) {
            throw new InvalidProductException("Product ID cannot be empty");
        }

        if (expectedPrice == null || expectedPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidProductException("Product price must be greater than zero");
        }

        // Extract JWT token from current request
        String jwtToken = extractJwtTokenFromCurrentRequest();
        
        // Call inventory service through AI load balancer to validate product exists
        Optional<InventoryItemDto> inventoryItem = inventoryServiceClient.getProductInventory(productId, jwtToken);
        
        if (inventoryItem.isEmpty()) {
            log.warn("Product not found in inventory: {}", productId);
            throw new InvalidProductException("Product not found: " + productId);
        }
        
        InventoryItemDto item = inventoryItem.get();
        
        // Check if product is out of stock
        if (Boolean.TRUE.equals(item.getOutOfStock())) {
            log.warn("Product is out of stock: {}", productId);
            throw new InvalidProductException("Product is out of stock: " + productId);
        }
        
        // Check if product has available quantity
        if (item.getAvailableQuantity() == null || item.getAvailableQuantity() <= 0) {
            log.warn("Product has no available quantity: {}", productId);
            throw new InvalidProductException("Product is not available: " + productId);
        }

        log.debug("Product validation successful for: {} (available: {})", productId, item.getAvailableQuantity());
    }
    
    public void validateProductAvailability(String productId, Integer requestedQuantity) {
        log.debug("Validating product availability: {} for quantity: {}", productId, requestedQuantity);
        
        // Extract JWT token from current request
        String jwtToken = extractJwtTokenFromCurrentRequest();
        
        if (!inventoryServiceClient.isProductAvailable(productId, requestedQuantity, jwtToken)) {
            throw new InvalidProductException(
                String.format("Insufficient stock for product %s. Requested: %d", productId, requestedQuantity)
            );
        }
        
        log.debug("Product availability validation successful for: {} (quantity: {})", productId, requestedQuantity);
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
