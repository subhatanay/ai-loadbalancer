package com.bits.cartservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {

    private String cartId;
    private String userId;
    private String sessionId;
    private List<CartItemResponse> items;
    private BigDecimal totalAmount;
    private Integer totalItems;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
