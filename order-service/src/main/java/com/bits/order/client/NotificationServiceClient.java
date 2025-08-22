package com.bits.order.client;

import com.bits.order.dto.CreateNotificationRequestDto;
import com.bits.order.dto.NotificationResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.bits.order.enums.NotificationType;
import com.bits.order.enums.NotificationPriority;
import com.bits.order.enums.NotificationChannel;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceClient {
    
    private final WebClient webClient;
    
    @Value("${services.notification-service.url}")
    private String notificationServiceUrl;
    
    public NotificationResponseDto sendOrderConfirmation(String userId, String orderNumber, 
                                                        String userEmail, String jwtToken) {
        try {
            log.info("Sending order confirmation notification for order: {}", orderNumber);
            
            CreateNotificationRequestDto request = CreateNotificationRequestDto.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .type(NotificationType.ORDER_CONFIRMATION)
                    .title("Order Confirmation")
                    .message("Your order " + orderNumber + " has been confirmed and is being processed.")
                    .channels(List.of(NotificationChannel.EMAIL))
                    .priority(NotificationPriority.HIGH)
                    .metadata(Map.of(
                        "orderNumber", orderNumber,
                        "type", "order_confirmation"
                    ))
                    .build();
            
            NotificationResponseDto response = webClient.post()
                    .uri(notificationServiceUrl + "/api/notifications")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(), error -> {
                        log.error("Notification service returned error: {}", error.statusCode());
                        return Mono.error(new RuntimeException("Failed to send order confirmation notification"));
                    })
                    .bodyToMono(NotificationResponseDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.info("Order confirmation notification sent successfully for order: {}", orderNumber);
            return response;
            
        } catch (Exception e) {
            log.error("Error sending order confirmation notification for order: {}", orderNumber, e);
            return null;
        }
    }
    
    public NotificationResponseDto sendPaymentConfirmation(String userId, String orderNumber, 
                                                          String userEmail, String paymentId, String jwtToken) {
        try {
            log.info("Sending payment confirmation notification for order: {}", orderNumber);
            
            CreateNotificationRequestDto request = CreateNotificationRequestDto.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .type(NotificationType.PAYMENT_SUCCESS)
                    .title("Payment Successful")
                    .message("Payment for your order " + orderNumber + " has been processed successfully.")
                    .channels(List.of(NotificationChannel.EMAIL))
                    .priority(NotificationPriority.HIGH)
                    .metadata(Map.of(
                        "orderNumber", orderNumber,
                        "paymentId", paymentId,
                        "type", "payment_confirmation"
                    ))
                    .build();
            
            NotificationResponseDto response = webClient.post()
                    .uri(notificationServiceUrl + "/api/notifications")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(), error -> {
                        log.error("Notification service returned error: {}", error.statusCode());
                        return Mono.error(new RuntimeException("Failed to send payment confirmation notification"));
                    })
                    .bodyToMono(NotificationResponseDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.info("Payment confirmation notification sent successfully for order: {}", orderNumber);
            return response;
            
        } catch (Exception e) {
            log.error("Error sending payment confirmation notification for order: {}", orderNumber, e);
            return null;
        }
    }
    
    public NotificationResponseDto sendOrderStatusUpdate(String userId, String orderNumber, 
                                                        String userEmail, String status, String jwtToken) {
        try {
            log.info("Sending order status update notification for order: {} - Status: {}", orderNumber, status);
            
            CreateNotificationRequestDto request = CreateNotificationRequestDto.builder()
                    .userId(userId)
                    .userEmail(userEmail)
                    .type(NotificationType.ORDER_STATUS_UPDATE)
                    .title("Order Status Update")
                    .message("Your order " + orderNumber + " status has been updated to: " + status)
                    .channels(List.of(NotificationChannel.EMAIL))
                    .priority(NotificationPriority.NORMAL)
                    .metadata(Map.of(
                        "orderNumber", orderNumber,
                        "status", status,
                        "type", "status_update"
                    ))
                    .build();
            
            NotificationResponseDto response = webClient.post()
                    .uri(notificationServiceUrl + "/api/notifications")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(), error -> {
                        log.error("Notification service returned error: {}", error.statusCode());
                        return Mono.error(new RuntimeException("Failed to send order status update notification"));
                    })
                    .bodyToMono(NotificationResponseDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.info("Order status update notification sent successfully for order: {}", orderNumber);
            return response;
            
        } catch (Exception e) {
            log.error("Error sending order status update notification for order: {}", orderNumber, e);
            return null;
        }
    }
}
