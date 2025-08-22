package com.bits.cartservice.service;

import com.bits.cartservice.event.*;
import com.bits.cartservice.model.Cart;
import com.bits.cartservice.model.CartItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public void publishCartCreatedEvent(Cart cart) {
        CartCreatedEvent event = CartCreatedEvent.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .sessionId(cart.getSessionId())
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send("cart-created", cart.getId(), event);
        log.info("Published cart created event for cart: {}", cart.getId());
    }
    
    public void publishCartItemAddedEvent(Cart cart, CartItem item) {
        CartItemAddedEvent event = CartItemAddedEvent.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .sessionId(cart.getSessionId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .price(item.getPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send("cart-item-added", cart.getId(), event);
        log.info("Published cart item added event for cart: {}, product: {}", cart.getId(), item.getProductId());
    }
    
    public void publishCartItemUpdatedEvent(Cart cart, String productId, Integer newQuantity) {
        CartItemUpdatedEvent event = CartItemUpdatedEvent.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .sessionId(cart.getSessionId())
                .productId(productId)
                .newQuantity(newQuantity)
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send("cart-item-updated", cart.getId(), event);
        log.info("Published cart item updated event for cart: {}, product: {}", cart.getId(), productId);
    }
    
    public void publishCartItemRemovedEvent(Cart cart, String productId) {
        CartItemRemovedEvent event = CartItemRemovedEvent.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .sessionId(cart.getSessionId())
                .productId(productId)
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send("cart-item-removed", cart.getId(), event);
        log.info("Published cart item removed event for cart: {}, product: {}", cart.getId(), productId);
    }
    
    public void publishCartClearedEvent(Cart cart) {
        CartClearedEvent event = CartClearedEvent.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .sessionId(cart.getSessionId())
                .itemsCleared(cart.getTotalItems())
                .amountCleared(cart.getTotalAmount())
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send("cart-cleared", cart.getId(), event);
        log.info("Published cart cleared event for cart: {}", cart.getId());
    }
    
    public void publishCartMergedEvent(Cart userCart, String anonymousCartId) {
        CartMergedEvent event = CartMergedEvent.builder()
                .userCartId(userCart.getId())
                .anonymousCartId(anonymousCartId)
                .userId(userCart.getUserId())
                .totalItemsMerged(userCart.getTotalItems())
                .totalAmountMerged(userCart.getTotalAmount())
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send("cart-merged", userCart.getId(), event);
        log.info("Published cart merged event - User cart: {}, Anonymous cart: {}", userCart.getId(), anonymousCartId);
    }
    
    public void publishCartConvertedToOrderEvent(Cart cart, String orderId) {
        CartConvertedToOrderEvent event = CartConvertedToOrderEvent.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .orderId(orderId)
                .totalItems(cart.getTotalItems())
                .totalAmount(cart.getTotalAmount())
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send("cart-converted-to-order", cart.getId(), event);
        log.info("Published cart converted to order event - Cart: {}, Order: {}", cart.getId(), orderId);
    }
}
