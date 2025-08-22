package com.bits.inventory.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryAdjustedEvent {
    
    private String eventId;
    private String productSku;
    private String warehouseLocation;
    private Integer quantityAdjustment;
    private Integer previousQuantity;
    private Integer newQuantity;
    private String reason;
    private String performedBy;
    private LocalDateTime timestamp;
}
