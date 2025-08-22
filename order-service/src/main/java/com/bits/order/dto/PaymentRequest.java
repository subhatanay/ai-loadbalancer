package com.bits.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    
    private Long orderId;
    private String orderNumber;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String description;
}
