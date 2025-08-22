package com.bits.payment.dto;

import com.bits.payment.enums.Currency;
import com.bits.payment.enums.PaymentMethod;
import com.bits.payment.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private String paymentId;
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private Currency currency;
    private PaymentMethod method;
    private String paymentReference;
    private PaymentStatus status;
    private String errorMessage;
    private BigDecimal refundAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
