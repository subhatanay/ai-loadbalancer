package com.bits.order.event;

import com.bits.order.model.OrderStatus;
import com.bits.order.model.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleOrderEventWithReason {
    
    private String eventType;
    private LocalDateTime timestamp;
    private Long orderId;
    private String orderNumber;
    private String userId;
    private OrderStatus status;
    private PaymentStatus paymentStatus;
    private String reason;
}
