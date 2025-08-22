package com.bits.order.event;

import com.bits.order.model.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusUpdatedEvent {
    
    private String eventType = "ORDER_STATUS_UPDATED";
    private LocalDateTime timestamp;
    private Long orderId;
    private String orderNumber;
    private String userId;
    private OrderStatus oldStatus;
    private OrderStatus newStatus;
}
