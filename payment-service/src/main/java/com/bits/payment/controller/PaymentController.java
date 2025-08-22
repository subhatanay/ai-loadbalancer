package com.bits.payment.controller;

import com.bits.payment.dto.*;
import com.bits.payment.security.JwtService;
import com.bits.payment.service.PaymentService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final JwtService jwtService;
    private final Counter paymentCreatedCounter;
    private final Counter refundCounter;

    @Autowired
    public PaymentController(PaymentService paymentService, JwtService jwtService, MeterRegistry meterRegistry) {
        this.paymentService = paymentService;
        this.jwtService = jwtService;
        this.paymentCreatedCounter = Counter.builder("payments_processed_total")
                .description("Total number of payments processed")
                .register(meterRegistry);
        this.refundCounter = Counter.builder("payments_refunded_total")
                .description("Total number of payment refunds")
                .register(meterRegistry);
    }

    private String extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtService.extractUserId(token);
        }
        return null;
    }

    @PostMapping
    @Timed(value = "payment_create_duration", description = "Time taken to process payment")
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody CreatePaymentRequest request, HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        log.info("Processing payment for order: {} by user: {}", request.getOrderId(), userId);
        PaymentResponse response = paymentService.processPayment(request);
        paymentCreatedCounter.increment();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/process")
    @Timed(value = "payment_process_duration", description = "Time taken to process payment via /process endpoint")
    public ResponseEntity<PaymentResponse> processPaymentAlias(
            @Valid @RequestBody CreatePaymentRequest request, HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        log.info("Processing payment request for order: {} by user: {}", request.getOrderId(), userId);
        PaymentResponse response = paymentService.processPayment(request);
        paymentCreatedCounter.increment();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{paymentId}")
    @Timed(value = "payment_get_duration", description = "Time taken to get payment status")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String paymentId, HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        log.debug("Fetching payment transaction: {} by user: {}", paymentId, userId);
        PaymentResponse response = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{paymentId}/refund")
    @Timed(value = "payment_refund_duration", description = "Time taken to process refund")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable String paymentId,
            @Valid @RequestBody RefundRequest request,
            HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        log.info("Initiating refund for payment: {} by user: {}", paymentId, userId);
        request.setPaymentId(paymentId);
        PaymentResponse response = paymentService.refund(request);
        refundCounter.increment();
        return ResponseEntity.ok(response);
    }
}
