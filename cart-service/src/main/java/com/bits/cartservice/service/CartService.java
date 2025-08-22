package com.bits.cartservice.service;

import com.bits.cartservice.dto.*;
import com.bits.cartservice.exception.*;
import com.bits.cartservice.model.Cart;
import com.bits.cartservice.model.CartItem;
import com.bits.cartservice.model.CartStatus;
import com.bits.cartservice.dao.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartEventPublisher eventPublisher;
    private final CartValidationService validationService;

    @Cacheable(value = "carts", key = "#userId != null ? #userId : 'anonymous'")
    public CartResponse getCartByUser(String userId) {
        log.debug("Fetching cart for user: {}", userId);

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> createNewCart(userId, null));

        return mapToCartResponse(cart);
    }

    @Cacheable(value = "carts", key = "#sessionId")
    public CartResponse getCartBySession(String sessionId) {
        log.debug("Fetching cart for session: {}", sessionId);

        Cart cart = cartRepository.findBySessionId(sessionId)
                .orElseGet(() -> createNewCart(null, sessionId));

        return mapToCartResponse(cart);
    }

    @CacheEvict(value = "carts", key = "#userId != null ? #userId : #sessionId")
    public CartResponse addToCart(String userId, String sessionId, AddToCartRequest request) {
        log.info("Adding item to cart - User: {}, Session: {}, Product: {}, Quantity: {}",
                userId, sessionId, request.getProductId(), request.getQuantity());

        // Validate product exists and price is correct
        validationService.validateProduct(request.getProductId(), request.getPrice());
        
        // Validate product availability for the requested quantity
        validationService.validateProductAvailability(request.getProductId(), request.getQuantity());

        Cart cart = getOrCreateCart(userId, sessionId);

        CartItem newItem = CartItem.builder()
                .productId(request.getProductId())
                .productName(request.getProductName())
                .productImage(request.getProductImage())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .subtotal(request.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())))
                .addedAt(LocalDateTime.now())
                .build();

        cart.addItem(newItem);
        cart.setUpdatedAt(LocalDateTime.now());

        Cart savedCart = cartRepository.save(cart);

        // Publish event
        eventPublisher.publishCartItemAddedEvent(savedCart, newItem);

        log.info("Item added to cart successfully - Cart ID: {}", savedCart.getId());
        return mapToCartResponse(savedCart);
    }

    @CacheEvict(value = "carts", key = "#userId != null ? #userId : #sessionId")
    public CartResponse updateCartItem(String userId, String sessionId, UpdateCartItemRequest request) {
        log.info("Updating cart item - User: {}, Session: {}, Product: {}, Quantity: {}",
                userId, sessionId, request.getProductId(), request.getQuantity());

        Cart cart = getExistingCart(userId, sessionId);

        if (request.getQuantity() == 0) {
            cart.removeItem(request.getProductId());
            eventPublisher.publishCartItemRemovedEvent(cart, request.getProductId());
        } else {
            // Validate product availability for the new quantity
            validationService.validateProductAvailability(request.getProductId(), request.getQuantity());
            
            cart.updateItemQuantity(request.getProductId(), request.getQuantity());
            eventPublisher.publishCartItemUpdatedEvent(cart, request.getProductId(), request.getQuantity());
        }

        cart.setUpdatedAt(LocalDateTime.now());
        Cart savedCart = cartRepository.save(cart);

        log.info("Cart item updated successfully - Cart ID: {}", savedCart.getId());
        return mapToCartResponse(savedCart);
    }

    @CacheEvict(value = "carts", key = "#userId != null ? #userId : #sessionId")
    public void removeFromCart(String userId, String sessionId, String productId) {
        log.info("Removing item from cart - User: {}, Session: {}, Product: {}",
                userId, sessionId, productId);

        Cart cart = getExistingCart(userId, sessionId);
        cart.removeItem(productId);
        cart.setUpdatedAt(LocalDateTime.now());

        cartRepository.save(cart);
        eventPublisher.publishCartItemRemovedEvent(cart, productId);

        log.info("Item removed from cart successfully - Cart ID: {}", cart.getId());
    }

    @CacheEvict(value = "carts", key = "#userId != null ? #userId : #sessionId")
    public void clearCart(String userId, String sessionId) {
        log.info("Clearing cart - User: {}, Session: {}", userId, sessionId);

        Cart cart = getExistingCart(userId, sessionId);
        cart.clearCart();

        cartRepository.save(cart);
        eventPublisher.publishCartClearedEvent(cart);

        log.info("Cart cleared successfully - Cart ID: {}", cart.getId());
    }

    public CartSummaryResponse getCartSummary(String userId, String sessionId) {
        log.debug("Getting cart summary - User: {}, Session: {}", userId, sessionId);

        Cart cart = getExistingCart(userId, sessionId);

        return CartSummaryResponse.builder()
                .cartId(cart.getId())
                .totalItems(cart.getTotalItems())
                .totalAmount(cart.getTotalAmount())
                .status(cart.getStatus().name())
                .build();
    }

    @CacheEvict(value = "carts", allEntries = true)
    public CartResponse mergeAnonymousCart(String userId, String anonymousCartId) {
        log.info("Merging anonymous cart {} with user cart for user: {}", anonymousCartId, userId);

        Cart anonymousCart = cartRepository.findById(anonymousCartId)
                .orElseThrow(() -> new CartNotFoundException("Anonymous cart not found: " + anonymousCartId));

        Cart userCart = cartRepository.findByUserId(userId)
                .orElseGet(() -> createNewCart(userId, null));

        // Merge items from anonymous cart to user cart
        for (CartItem item : anonymousCart.getItems()) {
            userCart.addItem(item);
        }

        userCart.setUpdatedAt(LocalDateTime.now());
        Cart mergedCart = cartRepository.save(userCart);

        // Delete anonymous cart
        cartRepository.delete(anonymousCart);

        eventPublisher.publishCartMergedEvent(mergedCart, anonymousCartId);

        log.info("Cart merged successfully - User Cart ID: {}", mergedCart.getId());
        return mapToCartResponse(mergedCart);
    }

    @CacheEvict(value = "carts", key = "#userId")
    public void convertCartToOrder(String userId, String orderId) {
        log.info("Converting cart to order - User: {}, Order: {}", userId, orderId);

        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));

        cart.setStatus(CartStatus.CONVERTED_TO_ORDER);
        cart.setUpdatedAt(LocalDateTime.now());

        cartRepository.save(cart);
        eventPublisher.publishCartConvertedToOrderEvent(cart, orderId);

        log.info("Cart converted to order successfully - Cart ID: {}, Order ID: {}", cart.getId(), orderId);
    }

    // Private helper methods

    private Cart getOrCreateCart(String userId, String sessionId) {
        if (userId != null) {
            return cartRepository.findByUserId(userId)
                    .orElseGet(() -> createNewCart(userId, null));
        } else {
            return cartRepository.findBySessionId(sessionId)
                    .orElseGet(() -> createNewCart(null, sessionId));
        }
    }

    private Cart getExistingCart(String userId, String sessionId) {
        if (userId != null) {
            return cartRepository.findByUserId(userId)
                    .orElseThrow(() -> new CartNotFoundException("Cart not found for user: " + userId));
        } else {
            return cartRepository.findBySessionId(sessionId)
                    .orElseThrow(() -> new CartNotFoundException("Cart not found for session: " + sessionId));
        }
    }

    private Cart createNewCart(String userId, String sessionId) {
        String cartId = generateCartId(userId, sessionId);

        Cart cart = Cart.builder()
                .id(cartId)
                .userId(userId)
                .sessionId(sessionId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(CartStatus.ACTIVE)
                .build();

        Cart savedCart = cartRepository.save(cart);
        eventPublisher.publishCartCreatedEvent(savedCart);

        log.info("New cart created - ID: {}", savedCart.getId());
        return savedCart;
    }

    private String generateCartId(String userId, String sessionId) {
        if (userId != null) {
            return "cart:user:" + userId;
        } else {
            return "cart:session:" + sessionId;
        }
    }

    private CartResponse mapToCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = cart.getItems().stream()
                .map(this::mapToCartItemResponse)
                .collect(Collectors.toList());

        return CartResponse.builder()
                .cartId(cart.getId())
                .userId(cart.getUserId())
                .sessionId(cart.getSessionId())
                .items(itemResponses)
                .totalAmount(cart.getTotalAmount())
                .totalItems(cart.getTotalItems())
                .status(cart.getStatus().name())
                .createdAt(cart.getCreatedAt())
                .updatedAt(cart.getUpdatedAt())
                .build();
    }

    private CartItemResponse mapToCartItemResponse(CartItem item) {
        return CartItemResponse.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productImage(item.getProductImage())
                .price(item.getPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .addedAt(item.getAddedAt())
                .build();
    }
}
