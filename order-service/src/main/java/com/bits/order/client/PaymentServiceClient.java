package com.bits.order.client;

import com.bits.order.dto.PaymentRequest;
import com.bits.order.dto.PaymentResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceClient {
    
    private final WebClient webClient;
    
    @Value("${services.payment-service.url}")
    private String paymentServiceUrl;
    
    public String processPayment(PaymentRequest request, String jwtToken) {
        try {
            log.info("Calling payment service to process payment for order: {}", request.getOrderNumber());
            
            PaymentResponseDto paymentResponse = webClient.post()
                    .uri(paymentServiceUrl + "/api/payments/process")
                    .header("Authorization", "Bearer " + jwtToken)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                        log.error("Payment service returned error: {}", response.statusCode());
                        return Mono.error(new RuntimeException("Payment processing failed"));
                    })
                    .bodyToMono(PaymentResponseDto.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            String paymentId = paymentResponse != null ? paymentResponse.getPaymentId() : null;
            log.info("Payment processed successfully for order: {} with payment ID: {}", 
                    request.getOrderNumber(), paymentId);
            return paymentId;
            
        } catch (Exception e) {
            log.error("Error calling payment service for order: {}", request.getOrderNumber(), e);
            return null;
        }
    }
    
    public boolean refundPayment(String paymentId, String jwtToken) {
        try {
            log.info("Calling payment service to refund payment: {}", paymentId);
            
            Boolean result = webClient.post()
                    .uri(paymentServiceUrl + "/api/payments/refund/{paymentId}", paymentId)
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                        log.error("Payment service returned error: {}", response.statusCode());
                        return Mono.error(new RuntimeException("Payment refund failed"));
                    })
                    .bodyToMono(Boolean.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            log.info("Payment refund result for payment {}: {}", paymentId, result);
            return Boolean.TRUE.equals(result);
            
        } catch (Exception e) {
            log.error("Error calling payment service to refund payment: {}", paymentId, e);
            return false;
        }
    }
    
    public String getPaymentStatus(String paymentId, String jwtToken) {
        try {
            log.debug("Checking payment status for payment: {}", paymentId);
            
            String status = webClient.get()
                    .uri(paymentServiceUrl + "/api/payments/{paymentId}", paymentId)
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(), response -> {
                        log.error("Payment service returned error: {}", response.statusCode());
                        return Mono.error(new RuntimeException("Payment status check failed"));
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            return status;
            
        } catch (Exception e) {
            log.error("Error checking payment status for payment: {}", paymentId, e);
            return "UNKNOWN";
        }
    }
}
