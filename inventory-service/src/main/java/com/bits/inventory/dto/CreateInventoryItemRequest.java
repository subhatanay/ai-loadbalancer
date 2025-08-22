package com.bits.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInventoryItemRequest {
    
    @NotBlank(message = "Product SKU is required")
    private String productSku;
    
    @NotBlank(message = "Warehouse location is required")
    private String warehouseLocation;
    
    @NotNull(message = "Total quantity is required")
    @PositiveOrZero(message = "Total quantity must be zero or positive")
    private Integer totalQuantity;
    
    @NotNull(message = "Available quantity is required")
    @PositiveOrZero(message = "Available quantity must be zero or positive")
    private Integer availableQuantity;
    
    @Builder.Default
    @PositiveOrZero(message = "Reserved quantity must be zero or positive")
    private Integer reservedQuantity = 0;
    
    @NotNull(message = "Minimum stock level is required")
    @PositiveOrZero(message = "Minimum stock level must be zero or positive")
    private Integer minimumStockLevel;
    
    private Integer maximumStockLevel;
    
    private Integer reorderPoint;
    
    @Positive(message = "Cost per unit must be positive")
    private BigDecimal costPerUnit;
    
    @Builder.Default
    private String status = "ACTIVE";
}
