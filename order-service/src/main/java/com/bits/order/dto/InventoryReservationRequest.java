package com.bits.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationRequest {
    
    private String orderId;
    private String productSku;
    private Integer quantity;
    private String warehouseLocation;
    private Integer reservationDurationMinutes;
}
