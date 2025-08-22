package com.bits.cartservice.event;

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
public class CartConvertedToOrderEvent {
    private String cartId;
    private String userId;
    private String orderId;
    private Integer totalItems;
    private BigDecimal totalAmount;
    private LocalDateTime timestamp;
    private String eventType = "CART_CONVERTED_TO_ORDER";
}
