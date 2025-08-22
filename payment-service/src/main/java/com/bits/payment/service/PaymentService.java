package com.bits.payment.service;

import com.bits.payment.dto.*;
import com.bits.payment.enums.PaymentMethod;
import com.bits.payment.enums.PaymentStatus;
import com.bits.payment.enums.Currency;
import com.bits.payment.exception.*;
import com.bits.payment.model.PaymentTransaction;
import com.bits.payment.repository.PaymentTransactionRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentTransactionRepository repository;
    private final PaymentEventPublisher eventPublisher;

    @Value("${payment.refund.delay-ms:1000}")
    private long refundDelayMs;

    public PaymentResponse processPayment(CreatePaymentRequest request) {
        log.info("Processing payment for order: {}, user: {}", request.getOrderId(), request.getUserId());

        // Build transaction
        String paymentId = generatePaymentId();
        PaymentTransaction txn = PaymentTransaction.builder()
                .paymentId(paymentId)
                .orderId(request.getOrderId() != null ? request.getOrderId().toString() : request.getOrderNumber())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? Currency.valueOf(request.getCurrency().toUpperCase()) : Currency.USD)
                .method(request.getMethod() != null ? request.getMethod() : 
                        (request.getPaymentMethod() != null ? PaymentMethod.valueOf(request.getPaymentMethod().toUpperCase()) : PaymentMethod.CREDIT_CARD))
                .paymentReference(request.getPaymentReference())
                .status(PaymentStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .build();

        // Simulate payment gateway logic:
        PaymentStatus finalStatus = PaymentStatus.COMPLETED;
        String errorMessage = null;
        // Random fail simulation (for demo - remove in prod)
        if (request.getAmount().compareTo(new BigDecimal("100000")) > 0) {
            finalStatus = PaymentStatus.FAILED;
            errorMessage = "Insufficient funds";
        }

        txn.setStatus(finalStatus);
        txn.setErrorMessage(errorMessage);
        PaymentTransaction savedTxn = repository.save(txn);

        // Event for payment result
        eventPublisher.publishPaymentEvent(savedTxn);

        log.info("Payment completed for order: {}, status: {}", request.getOrderId(), txn.getStatus());
        return mapToResponse(savedTxn);
    }

    @Cacheable(value = "payments", key = "#paymentId")
    public PaymentResponse getPayment(String paymentId) {
        log.debug("Fetching payment: {}", paymentId);
        PaymentTransaction txn = repository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("No payment found: " + paymentId));
        return mapToResponse(txn);
    }

    public PaymentResponse refund(RefundRequest req) {
        log.info("Processing refund for payment: {}", req.getPaymentId());
        PaymentTransaction txn = repository.findByPaymentId(req.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException("No payment found: " + req.getPaymentId()));

        if (txn.getStatus() != PaymentStatus.COMPLETED)
            throw new InvalidRefundException("Only completed payments can be refunded");

        if (req.getRefundAmount().compareTo(txn.getAmount()) > 0)
            throw new InvalidRefundException("Refund may not exceed payment amount");

        // Simulate refund gateway (delay)
        try { Thread.sleep(refundDelayMs); } catch (InterruptedException ignored) {}

        txn.setRefundAmount(req.getRefundAmount());
        txn.setStatus(PaymentStatus.REFUNDED);
        txn.setErrorMessage(req.getReason());
        PaymentTransaction x = repository.save(txn);

        eventPublisher.publishPaymentRefundedEvent(x);
        return mapToResponse(x);
    }

    // Helper
    private String generatePaymentId() {
        return "PAY-" + UUID.randomUUID();
    }
    private PaymentResponse mapToResponse(PaymentTransaction tx) {
        return PaymentResponse.builder()
                .paymentId(tx.getPaymentId())
                .orderId(tx.getOrderId())
                .userId(tx.getUserId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .method(tx.getMethod())
                .paymentReference(tx.getPaymentReference())
                .status(tx.getStatus())
                .errorMessage(tx.getErrorMessage())
                .refundAmount(tx.getRefundAmount())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .build();
    }
}
