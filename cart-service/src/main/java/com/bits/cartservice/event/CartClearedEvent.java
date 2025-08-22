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
public class CartClearedEvent {
    private String cartId;
    private String userId;
    private String sessionId;
    private Integer itemsCleared;
    private BigDecimal amountCleared;
    private LocalDateTime timestamp;
    private String eventType = "CART_CLEARED";
}
