package com.bits.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDto {
    private String paymentId;
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String currency; // Using String instead of enum for simplicity
    private String method; // Using String instead of enum for simplicity
    private String paymentReference;
    private String status; // Using String instead of enum for simplicity
    private String errorMessage;
    private BigDecimal refundAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
