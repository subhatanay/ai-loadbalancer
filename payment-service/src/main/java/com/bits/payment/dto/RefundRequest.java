package com.bits.payment.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest {
    @NotBlank private String paymentId;
    @NotNull @DecimalMin("0.01") private BigDecimal refundAmount;
    private String reason;
}
