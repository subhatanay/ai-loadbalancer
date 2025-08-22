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
public class CartMergedEvent {
    private String userCartId;
    private String anonymousCartId;
    private String userId;
    private Integer totalItemsMerged;
    private BigDecimal totalAmountMerged;
    private LocalDateTime timestamp;
    private String eventType = "CART_MERGED";
}
