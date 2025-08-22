package com.bits.payment.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRefundedEvent {
    private String paymentId;
    private String orderId;
    private String userId;
    private BigDecimal refundAmount;
    private String status;
    private LocalDateTime eventTime;
}
