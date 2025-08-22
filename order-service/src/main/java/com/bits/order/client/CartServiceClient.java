package com.bits.order.client;

import com.bits.order.dto.CartResponseDto;
import com.bits.order.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceClient {
    
    private final WebClient webClient;
    private final JwtService jwtService;
    
    @Value("${services.cart-service.url}")
    private String cartServiceUrl;
    
    public CartResponseDto getCart(String jwtToken) {
        String userId = null;
        try {
            userId = jwtService.extractUserId(jwtToken);
            log.info("Fetching cart contents for user: {}", userId);
            
            CartResponseDto cart = webClient.get()
                    .uri(cartServiceUrl + "/api/cart")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .header("X-User-ID", userId)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                        log.error("Cart service returned error while fetching cart: {}", response.statusCode());
                        return Mono.error(new RuntimeException("Failed to fetch cart"));
                    })
                    .bodyToMono(CartResponseDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.info("Successfully fetched cart for user {}: {} items, total: {}", 
                    userId, cart != null ? cart.getTotalItems() : 0, 
                    cart != null ? cart.getTotalAmount() : "0");
            return cart;
            
        } catch (Exception e) {
            log.error("Error fetching cart for user: {}", userId, e);
            return null;
        }
    }
    
    public boolean clearCart(String jwtToken) {
        String userId = null;
        try {
            userId = jwtService.extractUserId(jwtToken);
            log.info("Calling cart service to clear cart for user: {}", userId);
            
            webClient.delete()
                    .uri(cartServiceUrl + "/api/cart")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .header("X-User-ID", userId)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                        log.error("Cart service returned error: {}", response.statusCode());
                        return Mono.error(new RuntimeException("Cart clear failed"));
                    })
                    .bodyToMono(Void.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            log.info("Cart cleared successfully for user: {}", userId);
            return true;
            
        } catch (Exception e) {
            log.error("Error calling cart service to clear cart for user: {}", userId, e);
            return false;
        }
    }
    
    public boolean validateCartItems(String userId) {
        try {
            log.debug("Validating cart items for user: {}", userId);
            
            Boolean result = webClient.get()
                    .uri(cartServiceUrl + "/api/cart/validate/{userId}", userId)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), response -> {
                        log.error("Cart service returned error: {}", response.statusCode());
                        return Mono.error(new RuntimeException("Cart validation failed"));
                    })
                    .bodyToMono(Boolean.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            
            return Boolean.TRUE.equals(result);
            
        } catch (Exception e) {
            log.error("Error validating cart items for user: {}", userId, e);
            return false;
        }
    }
}
