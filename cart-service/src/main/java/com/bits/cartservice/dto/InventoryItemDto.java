package com.bits.cartservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemDto {
    
    private Long id;
    private String productSku;
    private String warehouseLocation;
    private Integer totalQuantity;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Integer minimumStockLevel;
    private Integer maximumStockLevel;
    private Integer reorderPoint;
    private Integer reorderQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean lowStock;
    private Boolean outOfStock;
}
