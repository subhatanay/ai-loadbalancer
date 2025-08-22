package com.bits.inventory.dto;

import com.bits.inventory.enums.AlertType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LowStockAlertResponse {
    
    private String alertId;
    private String productSku;
    private String warehouseLocation;
    private AlertType alertType;
    private Integer currentQuantity;
    private Integer thresholdQuantity;
    private String message;
    private Boolean resolved;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    
    // Manual builder implementation
    public static LowStockAlertResponseBuilder builder() {
        return new LowStockAlertResponseBuilder();
    }
    
    public static class LowStockAlertResponseBuilder {
        private String alertId;
        private String productSku;
        private String warehouseLocation;
        private AlertType alertType;
        private Integer currentQuantity;
        private Integer thresholdQuantity;
        private String message;
        private Boolean resolved;
        private LocalDateTime createdAt;
        private LocalDateTime resolvedAt;
        private String resolvedBy;
        
        public LowStockAlertResponseBuilder alertId(String alertId) {
            this.alertId = alertId;
            return this;
        }
        
        public LowStockAlertResponseBuilder productSku(String productSku) {
            this.productSku = productSku;
            return this;
        }
        
        public LowStockAlertResponseBuilder warehouseLocation(String warehouseLocation) {
            this.warehouseLocation = warehouseLocation;
            return this;
        }
        
        public LowStockAlertResponseBuilder alertType(AlertType alertType) {
            this.alertType = alertType;
            return this;
        }
        
        public LowStockAlertResponseBuilder currentQuantity(Integer currentQuantity) {
            this.currentQuantity = currentQuantity;
            return this;
        }
        
        public LowStockAlertResponseBuilder thresholdQuantity(Integer thresholdQuantity) {
            this.thresholdQuantity = thresholdQuantity;
            return this;
        }
        
        public LowStockAlertResponseBuilder message(String message) {
            this.message = message;
            return this;
        }
        
        public LowStockAlertResponseBuilder resolved(Boolean resolved) {
            this.resolved = resolved;
            return this;
        }
        
        public LowStockAlertResponseBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }
        
        public LowStockAlertResponseBuilder resolvedAt(LocalDateTime resolvedAt) {
            this.resolvedAt = resolvedAt;
            return this;
        }
        
        public LowStockAlertResponseBuilder resolvedBy(String resolvedBy) {
            this.resolvedBy = resolvedBy;
            return this;
        }
        
        public LowStockAlertResponse build() {
            LowStockAlertResponse response = new LowStockAlertResponse();
            response.alertId = this.alertId;
            response.productSku = this.productSku;
            response.warehouseLocation = this.warehouseLocation;
            response.alertType = this.alertType;
            response.currentQuantity = this.currentQuantity;
            response.thresholdQuantity = this.thresholdQuantity;
            response.message = this.message;
            response.resolved = this.resolved;
            response.createdAt = this.createdAt;
            response.resolvedAt = this.resolvedAt;
            response.resolvedBy = this.resolvedBy;
            return response;
        }
    }
}
