package com.bits.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentRequest {
    
    @NotBlank(message = "Product SKU is required")
    private String productSku;
    
    @NotNull(message = "Quantity adjustment is required")
    private Integer quantityAdjustment;
    
    private String warehouseLocation;
    
    private String reason;
    
    private String performedBy;
    
    // Getters and setters
    public String getProductSku() {
        return productSku;
    }
    
    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }
    
    public Integer getQuantityAdjustment() {
        return quantityAdjustment;
    }
    
    public void setQuantityAdjustment(Integer quantityAdjustment) {
        this.quantityAdjustment = quantityAdjustment;
    }
    
    public String getWarehouseLocation() {
        return warehouseLocation;
    }
    
    public void setWarehouseLocation(String warehouseLocation) {
        this.warehouseLocation = warehouseLocation;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public String getPerformedBy() {
        return performedBy;
    }
    
    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }
}
