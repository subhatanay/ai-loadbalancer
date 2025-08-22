package com.bits.order.dto;

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
public class CartResponseDto {
    
    private String cartId;
    private String userId;
    private String sessionId;
    private List<CartItemDto> items;
    private BigDecimal totalAmount;
    private Integer totalItems;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
