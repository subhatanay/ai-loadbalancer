package com.bits.inventory.service;

import com.bits.inventory.dto.ReservationRequest;
import com.bits.inventory.dto.StockAdjustmentRequest;
import com.bits.inventory.exception.InventoryValidationException;
import org.springframework.stereotype.Service;

@Service
public class InventoryValidationService {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InventoryValidationService.class);
    
    public void validateReservationRequest(ReservationRequest request) {
        if (request.getOrderId() == null || request.getOrderId().trim().isEmpty()) {
            throw new InventoryValidationException("Order ID is required");
        }
        
        if (request.getProductSku() == null || request.getProductSku().trim().isEmpty()) {
            throw new InventoryValidationException("Product SKU is required");
        }
        
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new InventoryValidationException("Quantity must be greater than 0");
        }
        
        if (request.getReservationDurationMinutes() != null && request.getReservationDurationMinutes() <= 0) {
            throw new InventoryValidationException("Reservation duration must be greater than 0");
        }
        
        logger.debug("Reservation request validation passed for order: {}", request.getOrderId());
    }
    
    public void validateStockAdjustmentRequest(StockAdjustmentRequest request) {
        if (request.getProductSku() == null || request.getProductSku().trim().isEmpty()) {
            throw new InventoryValidationException("Product SKU is required");
        }
        
        if (request.getQuantityAdjustment() == null) {
            throw new InventoryValidationException("Quantity adjustment is required");
        }
        
        logger.debug("Stock adjustment request validation passed for product: {}", request.getProductSku());
    }
    
    public void validateProductSku(String productSku) {
        if (productSku == null || productSku.trim().isEmpty()) {
            throw new InventoryValidationException("Product SKU is required");
        }
        
        if (productSku.length() > 100) {
            throw new InventoryValidationException("Product SKU cannot exceed 100 characters");
        }
    }
    
    public void validateWarehouseLocation(String warehouseLocation) {
        if (warehouseLocation != null && warehouseLocation.length() > 100) {
            throw new InventoryValidationException("Warehouse location cannot exceed 100 characters");
        }
    }
    
    public void validateQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new InventoryValidationException("Quantity must be greater than 0");
        }
    }
}
