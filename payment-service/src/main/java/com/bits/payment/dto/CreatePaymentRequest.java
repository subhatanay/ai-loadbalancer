package com.bits.payment.dto;

import com.bits.payment.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {
    private Long orderId;
    private String orderNumber;
    @NotBlank private String userId;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String description;
    private String paymentReference; // External ref or tokenized card
    
    // Legacy support for existing enum fields
    private PaymentMethod method;
}
