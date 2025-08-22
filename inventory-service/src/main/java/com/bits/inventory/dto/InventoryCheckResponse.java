package com.bits.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCheckResponse {
    
    private String productSku;
    private Boolean available;
    private Integer availableQuantity;
    private Integer requestedQuantity;
    private String warehouseLocation;
    private String message;
}
