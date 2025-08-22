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
public class InventoryReservedEvent {
    
    private String eventId;
    private String reservationId;
    private String orderId;
    private String productSku;
    private Integer reservedQuantity;
    private String warehouseLocation;
    private LocalDateTime expiresAt;
    private LocalDateTime timestamp;
}
