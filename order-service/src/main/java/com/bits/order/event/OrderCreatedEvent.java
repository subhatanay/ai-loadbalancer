package com.bits.order.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    
    private String eventType = "ORDER_CREATED";
    private LocalDateTime timestamp;
    private Long orderId;
    private String orderNumber;
    private String userId;
    private String userEmail;
    private BigDecimal totalAmount;
    private Integer totalItems;
    private List<OrderItemEvent> items;
    private String sagaId;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemEvent {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal subtotal;
    }
}
