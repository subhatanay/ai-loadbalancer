package com.bits.order.client;

import com.bits.order.dto.InventoryReservationRequest;
import com.bits.order.dto.ReservationResponseDto;
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
public class InventoryServiceClient {
    
    private final WebClient webClient;
    
    @Value("${services.inventory-service.url}")
    private String inventoryServiceUrl;
    
    public ReservationResponseDto reserveInventory(InventoryReservationRequest request, String jwtToken) {
        try {
            log.info("Calling inventory service to reserve inventory for order: {} product: {} quantity: {}", 
                    request.getOrderId(), request.getProductSku(), request.getQuantity());
            
            ReservationResponseDto result = webClient.post()
                    .uri(inventoryServiceUrl + "/api/inventory/reserve")
                    .header("Authorization", "Bearer " + jwtToken)
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                        log.error("Inventory service returned error: {}", response.statusCode());
                        return Mono.error(new RuntimeException("Inventory reservation failed"));
                    })
                    .bodyToMono(ReservationResponseDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.info("Inventory reservation result for order {} product {}: success={}, reservationId={}", 
                    request.getOrderId(), request.getProductSku(), 
                    result != null ? result.getSuccess() : false, 
                    result != null ? result.getReservationId() : null);
            return result;
            
        } catch (Exception e) {
            log.error("Error calling inventory service for order: {} product: {}", 
                    request.getOrderId(), request.getProductSku(), e);
            return null;
        }
    }
    
    public boolean releaseInventory(String reservationId, String jwtToken) {
        try {
            log.info("Calling inventory service to release reservation: {}", reservationId);
            
            String result = webClient.post()
                    .uri(inventoryServiceUrl + "/api/inventory/release/{reservationId}", reservationId)
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                        log.error("Inventory service returned error: {}", response.statusCode());
                        return Mono.error(new RuntimeException("Inventory release failed"));
                    })
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.info("Inventory release result for reservation {}: {}", reservationId, result);
            return result != null && result.contains("successfully");
            
        } catch (Exception e) {
            log.error("Error calling inventory service to release reservation: {}", reservationId, e);
            return false;
        }
    }
    
    public boolean checkInventoryAvailability(String productId, int quantity, String jwtToken) {
        try {
            log.debug("Checking inventory availability for product: {} quantity: {}", productId, quantity);
            
            Boolean result = webClient.get()
                    .uri(inventoryServiceUrl + "/api/inventory/check/{productId}?quantity={quantity}", 
                         productId, quantity)
                    .header("Authorization", "Bearer " + jwtToken)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                        log.error("Inventory service returned error: {}", response.statusCode());
                        return Mono.error(new RuntimeException("Inventory check failed"));
                    })
                    .bodyToMono(Boolean.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            
            return Boolean.TRUE.equals(result);
            
        } catch (Exception e) {
            log.error("Error checking inventory availability for product: {}", productId, e);
            return false;
        }
    }
}
