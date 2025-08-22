package com.bits.payment.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentProcessedEvent {
    private String paymentId;
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String status;
    private String method;
    private LocalDateTime createdAt;
    private LocalDateTime eventTime;
}
