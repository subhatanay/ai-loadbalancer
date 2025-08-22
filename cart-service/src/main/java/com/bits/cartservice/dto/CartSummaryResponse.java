package com.bits.cartservice.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartSummaryResponse {

    private String cartId;
    private Integer totalItems;
    private BigDecimal totalAmount;
    private String status;
}
