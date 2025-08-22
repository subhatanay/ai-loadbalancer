package com.bits.order.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {
    
    private String eventType = "ORDER_CANCELLED";
    private LocalDateTime timestamp;
    private Long orderId;
    private String orderNumber;
    private String userId;
    private String userEmail;
    private BigDecimal totalAmount;
    private String cancellationReason;
}
