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
public class CartCreatedEvent {
    private String cartId;
    private String userId;
    private String sessionId;
    private LocalDateTime timestamp;
    private String eventType = "CART_CREATED";
}
