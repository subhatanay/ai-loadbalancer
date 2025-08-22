package com.bits.cartservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemRemovedEvent {
    private String cartId;
    private String userId;
    private String sessionId;
    private String productId;
    private LocalDateTime timestamp;
    private String eventType = "CART_ITEM_REMOVED";
}
