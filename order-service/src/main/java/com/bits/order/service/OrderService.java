package com.bits.order.service;

import com.bits.order.client.CartServiceClient;
import com.bits.order.client.NotificationServiceClient;
import com.bits.order.dto.*;
import com.bits.order.event.OrderEventPublisher;
import com.bits.order.exception.OrderNotFoundException;
import com.bits.order.exception.OrderValidationException;
import com.bits.order.model.*;
import com.bits.order.repository.OrderRepository;
import com.bits.order.saga.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final OrderSagaOrchestrator sagaOrchestrator;
    private final OrderValidationService validationService;
    private final CartServiceClient cartServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    
    @Transactional
    @CacheEvict(value = "user-orders", allEntries = true)
    public OrderResponse createOrder(String userId, String userEmail, CreateOrderRequest request, String jwtToken) {
        log.info("Creating order for user: {}", userId);
        
        // Validate request
        validationService.validateCreateOrderRequest(request);
        
        // Validate and fetch cart contents
        CartResponseDto cart = validateAndFetchCart(userId, jwtToken);
        
        // Generate order number and saga ID
        String orderNumber = generateOrderNumber();
        String sagaId = UUID.randomUUID().toString();
        
        // Build order from cart contents
        Order order = buildOrderFromCart(userId, userEmail, cart, orderNumber, sagaId, jwtToken);
        
        // Save order
        Order savedOrder = orderRepository.save(order);
        log.info("Order created with ID: {} and number: {}", savedOrder.getId(), savedOrder.getOrderNumber());
        
        // Publish order created event
        eventPublisher.publishOrderCreatedEvent(savedOrder);
        
        // Send order confirmation notification
        try {
            notificationServiceClient.sendOrderConfirmation(
                savedOrder.getUserId(), 
                savedOrder.getOrderNumber(), 
                savedOrder.getUserEmail(), 
                jwtToken
            );
            log.info("Order confirmation notification sent for order: {}", savedOrder.getOrderNumber());
        } catch (Exception e) {
            log.warn("Failed to send order confirmation notification for order: {}", savedOrder.getOrderNumber(), e);
        }
        
        // Start saga orchestration
        sagaOrchestrator.startOrderProcessingSaga(savedOrder);
        
        return mapToOrderResponse(savedOrder);
    }
    
    @Cacheable(value = "orders", key = "#p0")
    public OrderResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + orderId));
        return mapToOrderResponse(order);
    }
    
    @Cacheable(value = "orders", key = "#p0")
    public OrderResponse getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with number: " + orderNumber));
        return mapToOrderResponse(order);
    }
    
    @Cacheable(value = "user-orders", key = "#p0")
    public List<OrderResponse> getUserOrders(String userId) {
        List<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }
    
    public Page<OrderResponse> getUserOrdersPaginated(String userId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return orders.map(this::mapToOrderResponse);
    }
    
    @Transactional
    @CacheEvict(value = {"orders", "user-orders"}, allEntries = true)
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + orderId));
        
        // Validate status transition
        validationService.validateStatusTransition(order.getStatus(), newStatus);
        
        OrderStatus oldStatus = order.getStatus();
        order.updateStatus(newStatus);
        
        Order updatedOrder = orderRepository.save(order);
        log.info("Order {} status updated from {} to {}", orderId, oldStatus, newStatus);
        
        // Publish status update event
        eventPublisher.publishOrderStatusUpdatedEvent(updatedOrder, oldStatus);
        
        return mapToOrderResponse(updatedOrder);
    }
    
    @Transactional
    @CacheEvict(value = {"orders", "user-orders"}, allEntries = true)
    public OrderResponse cancelOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + orderId));
        
        // Validate cancellation
        validationService.validateOrderCancellation(order);
        
        OrderStatus oldStatus = order.getStatus();
        order.updateStatus(OrderStatus.CANCELLED);
        
        Order cancelledOrder = orderRepository.save(order);
        log.info("Order {} cancelled. Reason: {}", orderId, reason);
        
        // Publish cancellation event
        eventPublisher.publishOrderCancelledEvent(cancelledOrder, reason);
        
        // Start compensation saga
        sagaOrchestrator.startOrderCancellationSaga(cancelledOrder);
        
        return mapToOrderResponse(cancelledOrder);
    }
    
    @Transactional
    @CacheEvict(value = {"orders", "user-orders"}, allEntries = true)
    public OrderResponse updatePaymentStatus(Long orderId, PaymentStatus paymentStatus, String paymentId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with ID: " + orderId));
        
        order.updatePaymentStatus(paymentStatus);
        if (paymentId != null) {
            order.setPaymentId(paymentId);
        }
        
        Order updatedOrder = orderRepository.save(order);
        log.info("Order {} payment status updated to {}", orderId, paymentStatus);
        
        // Publish payment status update event
        eventPublisher.publishOrderPaymentUpdatedEvent(updatedOrder);
        
        return mapToOrderResponse(updatedOrder);
    }
    
    public List<OrderResponse> getOrdersByStatus(OrderStatus status) {
        List<Order> orders = orderRepository.findByStatus(status);
        return orders.stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }
    
    private Order buildOrderFromRequest(String userId, String userEmail, CreateOrderRequest request, 
                                       String orderNumber, String sagaId) {
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .userId(userId)
                .userEmail(userEmail)
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .notes(request.getNotes())
                .sagaId(sagaId)
                .shippingAddress(mapToShippingAddress(request.getShippingAddress()))
                .build();
        
        // Add order items
        for (OrderItemRequest itemRequest : request.getItems()) {
            OrderItem orderItem = OrderItem.builder()
                    .productId(itemRequest.getProductId())
                    .productName(itemRequest.getProductName())
                    .productImage(itemRequest.getProductImage())
                    .price(itemRequest.getPrice())
                    .quantity(itemRequest.getQuantity())
                    .build();
            
            order.addOrderItem(orderItem);
        }
        
        return order;
    }
    
    private ShippingAddress mapToShippingAddress(ShippingAddressRequest request) {
        return ShippingAddress.builder()
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .country(request.getCountry())
                .phone(request.getPhone())
                .build();
    }
    
    private OrderResponse mapToOrderResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .items(order.getOrderItems().stream()
                        .map(this::mapToOrderItemResponse)
                        .collect(Collectors.toList()))
                .totalAmount(order.getTotalAmount())
                .totalItems(order.getTotalItems())
                .status(order.getStatus())
                .paymentId(order.getPaymentId())
                .paymentStatus(order.getPaymentStatus())
                .shippingAddress(mapToShippingAddressResponse(order.getShippingAddress()))
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
    
    private OrderItemResponse mapToOrderItemResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productImage(item.getProductImage())
                .price(item.getPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .build();
    }
    
    private ShippingAddressResponse mapToShippingAddressResponse(ShippingAddress address) {
        if (address == null) return null;
        
        return ShippingAddressResponse.builder()
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .zipCode(address.getZipCode())
                .country(address.getCountry())
                .phone(address.getPhone())
                .build();
    }
    
    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private CartResponseDto validateAndFetchCart(String userId, String jwtToken) {
        log.info("Validating and fetching cart for user: {}", userId);
        
        CartResponseDto cart = cartServiceClient.getCart(jwtToken);
        
        if (cart == null) {
            throw new OrderValidationException("Cart not found for user: " + userId);
        }
        
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new OrderValidationException("Cart is empty for user: " + userId);
        }
        
        if (cart.getTotalAmount() == null || cart.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderValidationException("Invalid cart total amount for user: " + userId);
        }
        
        log.info("Cart validated successfully for user: {} - {} items, total: {}", 
                userId, cart.getTotalItems(), cart.getTotalAmount());
        
        return cart;
    }
    
    private Order buildOrderFromCart(String userId, String userEmail, CartResponseDto cart, 
                                    String orderNumber, String sagaId, String jwtToken) {
        log.info("Building order from cart for user: {}", userId);
        
        Order order = Order.builder()
                .orderNumber(orderNumber)
                .userId(userId)
                .userEmail(userEmail)
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .sagaId(sagaId)
                .jwtToken(jwtToken)
                // Note: Shipping address would need to be provided separately or stored in user profile
                .build();
        
        // Convert cart items to order items
        for (CartItemDto cartItem : cart.getItems()) {
            BigDecimal subtotal = cartItem.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            
            OrderItem orderItem = OrderItem.builder()
                    .productId(cartItem.getProductId())
                    .productName(cartItem.getProductName())
                    .price(cartItem.getPrice())
                    .quantity(cartItem.getQuantity())
                    .subtotal(subtotal)
                    .build();
            
            order.addOrderItem(orderItem);
        }
        
        log.info("Order built from cart - Order: {}, Items: {}, Total: {}", 
                orderNumber, order.getTotalItems(), order.getTotalAmount());
        
        return order;
    }
}
