package com.bits.order.controller;

import com.bits.order.dto.*;
import com.bits.order.model.OrderStatus;
import com.bits.order.service.OrderService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@Slf4j
public class OrderController {
    
    private final OrderService orderService;
    private final Counter orderCreationCounter;
    private final Counter orderRetrievalCounter;
    private final Counter orderUpdateCounter;
    private final Counter orderCancellationCounter;
    
    public OrderController(OrderService orderService, MeterRegistry meterRegistry) {
        this.orderService = orderService;
        this.orderCreationCounter = Counter.builder("order_creation_total")
                .description("Total number of order creation requests")
                .register(meterRegistry);
        this.orderRetrievalCounter = Counter.builder("order_retrieval_total")
                .description("Total number of order retrieval requests")
                .register(meterRegistry);
        this.orderUpdateCounter = Counter.builder("order_update_total")
                .description("Total number of order update requests")
                .register(meterRegistry);
        this.orderCancellationCounter = Counter.builder("order_cancellation_total")
                .description("Total number of order cancellation requests")
                .register(meterRegistry);
    }
    
    @PostMapping
    @Timed(value = "order_creation_time", description = "Time taken to create an order")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest) {
        
        orderCreationCounter.increment();
        
        String userId = extractUserId(httpRequest);
        String userEmail = extractUserEmail(httpRequest);
        
        log.info("Creating order for user: {} with {} items", userId, request.getItems().size());
        
        String jwtToken = extractJwtToken(httpRequest);
        OrderResponse orderResponse = orderService.createOrder(userId, userEmail, request, jwtToken);
        
        log.info("Order created successfully: {}", orderResponse.getOrderNumber());
        return new ResponseEntity<>(orderResponse, HttpStatus.CREATED);
    }
    
    @GetMapping("/{orderId}")
    @Timed(value = "order_retrieval_time", description = "Time taken to retrieve an order")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable("orderId") Long orderId) {
        orderRetrievalCounter.increment();
        
        log.debug("Retrieving order with ID: {}", orderId);
        OrderResponse orderResponse = orderService.getOrderById(orderId);
        
        return ResponseEntity.ok(orderResponse);
    }
    
    @GetMapping("/number/{orderNumber}")
    @Timed(value = "order_retrieval_time", description = "Time taken to retrieve an order")
    public ResponseEntity<OrderResponse> getOrderByNumber(@PathVariable("orderNumber") String orderNumber) {
        orderRetrievalCounter.increment();
        
        log.debug("Retrieving order with number: {}", orderNumber);
        OrderResponse orderResponse = orderService.getOrderByNumber(orderNumber);
        
        return ResponseEntity.ok(orderResponse);
    }
    
    @GetMapping("/user")
    @Timed(value = "user_orders_retrieval_time", description = "Time taken to retrieve user orders")
    public ResponseEntity<List<OrderResponse>> getUserOrders(HttpServletRequest httpRequest) {
        orderRetrievalCounter.increment();
        
        String userId = extractUserId(httpRequest);
        log.debug("Retrieving orders for user: {}", userId);
        
        List<OrderResponse> orders = orderService.getUserOrders(userId);
        return ResponseEntity.ok(orders);
    }
    
    @GetMapping("/user/paginated")
    @Timed(value = "user_orders_paginated_retrieval_time", description = "Time taken to retrieve paginated user orders")
    public ResponseEntity<Page<OrderResponse>> getUserOrdersPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest) {
        
        orderRetrievalCounter.increment();
        
        String userId = extractUserId(httpRequest);
        Pageable pageable = PageRequest.of(page, size);
        
        log.debug("Retrieving paginated orders for user: {} (page: {}, size: {})", userId, page, size);
        
        Page<OrderResponse> orders = orderService.getUserOrdersPaginated(userId, pageable);
        return ResponseEntity.ok(orders);
    }
    
    @PutMapping("/{orderId}/status")
    @Timed(value = "order_status_update_time", description = "Time taken to update order status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable("orderId") Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        
        orderUpdateCounter.increment();
        
        log.info("Updating order {} status to: {}", orderId, request.getStatus());
        
        OrderResponse orderResponse = orderService.updateOrderStatus(orderId, request.getStatus());
        
        log.info("Order {} status updated successfully to: {}", orderId, request.getStatus());
        return ResponseEntity.ok(orderResponse);
    }
    
    @PostMapping("/{orderId}/cancel")
    @Timed(value = "order_cancellation_time", description = "Time taken to cancel an order")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable("orderId") Long orderId,
            @RequestParam(required = false, defaultValue = "Customer requested cancellation") String reason) {
        
        orderCancellationCounter.increment();
        
        log.info("Cancelling order: {} with reason: {}", orderId, reason);
        
        OrderResponse orderResponse = orderService.cancelOrder(orderId, reason);
        
        log.info("Order {} cancelled successfully", orderId);
        return ResponseEntity.ok(orderResponse);
    }
    
    @GetMapping("/status/{status}")
    @Timed(value = "orders_by_status_retrieval_time", description = "Time taken to retrieve orders by status")
    public ResponseEntity<List<OrderResponse>> getOrdersByStatus(@PathVariable("status") OrderStatus status) {
        orderRetrievalCounter.increment();
        
        log.debug("Retrieving orders with status: {}", status);
        
        List<OrderResponse> orders = orderService.getOrdersByStatus(status);
        return ResponseEntity.ok(orders);
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Order Service is healthy");
    }
    
    private String extractUserId(HttpServletRequest request) {
        // First try to get from request attributes (set by JWT filter)
        String userId = (String) request.getAttribute("userId");
        
        if (userId != null) {
            return userId;
        }
        
        // Fallback to security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof String) {
            return (String) authentication.getPrincipal();
        }
        
        throw new IllegalStateException("User ID not found in request context");
    }
    
    private String extractJwtToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // Remove "Bearer " prefix
        }
        throw new IllegalStateException("JWT token not found in Authorization header");
    }
    
    private String extractUserEmail(HttpServletRequest request) {
        // First try to get from request attributes (set by JWT filter)
        String userEmail = (String) request.getAttribute("userEmail");
        
        if (userEmail != null) {
            return userEmail;
        }
        
        // If not available, return a default or throw exception
        log.warn("User email not found in request context");
        return "unknown@example.com"; // Default fallback
    }
}
