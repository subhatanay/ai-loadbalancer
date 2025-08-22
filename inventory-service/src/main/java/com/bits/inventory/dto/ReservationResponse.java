package com.bits.inventory.dto;

import com.bits.inventory.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {
    
    private String reservationId;
    private String orderId;
    private String productSku;
    private Integer reservedQuantity;
    private String warehouseLocation;
    private ReservationStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Boolean success;
    private String message;
}
