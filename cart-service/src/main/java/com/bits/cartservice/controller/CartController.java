package com.bits.cartservice.controller;

import com.bits.cartservice.dto.*;
import com.bits.cartservice.service.CartService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@Slf4j
public class CartController {
    
    private final CartService cartService;
    private final Counter addToCartCounter;
    private final Counter cartViewCounter;
    private final DegradationController degradationController;
    
    public CartController(CartService cartService, MeterRegistry meterRegistry, DegradationController degradationController) {
        this.cartService = cartService;
        this.degradationController = degradationController;
        this.addToCartCounter = Counter.builder("cart_items_added_total")
                .description("Total number of items added to cart")
                .register(meterRegistry);
        this.cartViewCounter = Counter.builder("cart_views_total")
                .description("Total number of cart views")
                .register(meterRegistry);
    }
    
    @GetMapping
    @Timed(value = "cart_fetch_duration", description = "Time taken to fetch cart")
    public ResponseEntity<CartResponse> getCart(HttpServletRequest request) {
        String userId = extractUserId(request);
        String sessionId = extractOrCreateSessionId(request);
        
        log.info("Fetching cart for user: {}, session: {}", userId, sessionId);

        // Inject artificial latency if active
        degradationController.injectLatencyIfActive();
        
        // Inject 500 errors if active
        if (degradationController.shouldInjectError()) {
            throw new RuntimeException("Simulated cart service error for testing");
        }
        
        CartResponse response;
        if (userId != null) {
            response = cartService.getCartByUser(userId);
        } else {
            response = cartService.getCartBySession(sessionId);
        }
        
        cartViewCounter.increment();
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/items")
    @Timed(value = "cart_add_item_duration", description = "Time taken to add item to cart")
    public ResponseEntity<CartResponse> addToCart(
            @Valid @RequestBody AddToCartRequest request,
            HttpServletRequest httpRequest) {
        
        // Inject artificial latency if active
        degradationController.injectLatencyIfActive();
        
        // Inject 500 errors if active
        if (degradationController.shouldInjectError()) {
            throw new RuntimeException("Simulated cart service error for testing");
        }
        
        String userId = extractUserId(httpRequest);
        String sessionId = extractOrCreateSessionId(httpRequest);
        
        log.info("Adding item to cart - User: {}, Session: {}, Product: {}", 
                userId, sessionId, request.getProductId());
        
        CartResponse response = cartService.addToCart(userId, sessionId, request);
        addToCartCounter.increment();
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PutMapping("/items")
    @Timed(value = "cart_update_item_duration", description = "Time taken to update cart item")
    public ResponseEntity<CartResponse> updateCartItem(
            @Valid @RequestBody UpdateCartItemRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        String sessionId = extractOrCreateSessionId(httpRequest);
        
        log.info("Updating cart item - User: {}, Session: {}, Product: {}", 
                userId, sessionId, request.getProductId());
        
        CartResponse response = cartService.updateCartItem(userId, sessionId, request);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/items/{productId}")
    @Timed(value = "cart_remove_item_duration", description = "Time taken to remove item from cart")
    public ResponseEntity<Void> removeFromCart(
            @PathVariable String productId,
            HttpServletRequest request) {
        
        String userId = extractUserId(request);
        String sessionId = extractOrCreateSessionId(request);
        
        log.info("Removing item from cart - User: {}, Session: {}, Product: {}", 
                userId, sessionId, productId);
        
        cartService.removeFromCart(userId, sessionId, productId);
        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping
    @Timed(value = "cart_clear_duration", description = "Time taken to clear cart")
    public ResponseEntity<Void> clearCart(HttpServletRequest request) {
        String userId = extractUserId(request);
        String sessionId = extractOrCreateSessionId(request);
        
        log.info("Clearing cart - User: {}, Session: {}", userId, sessionId);
        
        cartService.clearCart(userId, sessionId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/summary")
    @Timed(value = "cart_summary_duration", description = "Time taken to get cart summary")
    public ResponseEntity<CartSummaryResponse> getCartSummary(HttpServletRequest request) {
        String userId = extractUserId(request);
        String sessionId = extractOrCreateSessionId(request);
        
        log.debug("Getting cart summary - User: {}, Session: {}", userId, sessionId);
        
        CartSummaryResponse response = cartService.getCartSummary(userId, sessionId);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/merge")
    @Timed(value = "cart_merge_duration", description = "Time taken to merge carts")
    public ResponseEntity<CartResponse> mergeCart(
            @Valid @RequestBody MergeCartRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        if (userId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        log.info("Merging cart - User: {}, Anonymous Cart: {}", userId, request.getAnonymousCartId());
        
        CartResponse response = cartService.mergeAnonymousCart(userId, request.getAnonymousCartId());
        return ResponseEntity.ok(response);
    }
    
    // Internal endpoint for order service
    @PostMapping("/{userId}/convert-to-order")
    public ResponseEntity<Void> convertCartToOrder(
            @PathVariable String userId,
            @RequestParam String orderId) {
        
        log.info("Converting cart to order - User: {}, Order: {}", userId, orderId);
        
        cartService.convertCartToOrder(userId, orderId);
        return ResponseEntity.ok().build();
    }
    
    // Helper methods
    
    private String extractUserId(HttpServletRequest request) {
        // First try to get from JWT token (set by JwtAuthenticationFilter)
        String userId = (String) request.getAttribute("X-User-ID");
        
        // Fallback to header (for backward compatibility or service-to-service calls)
        if (userId == null) {
            userId = request.getHeader("X-User-ID");
        }
        
        // If still null, try to get from security context
        if (userId == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                // This would be the email from JWT, we might need a service call to get ID
                return authentication.getName(); // This is actually email, might need conversion
            }
        }
        
        return userId;
    }
    
    private String extractOrCreateSessionId(HttpServletRequest request) {
        String sessionId = request.getHeader("X-Session-ID");
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        return sessionId;
    }
}
