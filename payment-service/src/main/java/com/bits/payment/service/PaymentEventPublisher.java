package com.bits.payment.service;

import com.bits.payment.model.PaymentTransaction;
import com.bits.payment.event.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentEvent(PaymentTransaction txn) {
        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .paymentId(txn.getPaymentId())
                .orderId(txn.getOrderId())
                .userId(txn.getUserId())
                .amount(txn.getAmount())
                .status(txn.getStatus().name())
                .method(txn.getMethod().name())
                .createdAt(txn.getCreatedAt())
                .eventTime(LocalDateTime.now())
                .build();
        kafkaTemplate.send("payment-processed", txn.getPaymentId(), event);
    }

    public void publishPaymentRefundedEvent(PaymentTransaction txn) {
        PaymentRefundedEvent event = PaymentRefundedEvent.builder()
                .paymentId(txn.getPaymentId())
                .orderId(txn.getOrderId())
                .userId(txn.getUserId())
                .refundAmount(txn.getRefundAmount())
                .status(txn.getStatus().name())
                .eventTime(LocalDateTime.now())
                .build();
        kafkaTemplate.send("payment-refunded", txn.getPaymentId(), event);
    }
}
