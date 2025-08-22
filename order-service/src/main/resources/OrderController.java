package com.bits.order.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
@Validated
public class OrderController {

    private final OrderProcessingService orderProcessingService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("Received order request for customer: {}", request.getCustomerId());

        OrderResponse response = orderProcessingService.processOrder(request);

        log.info("Order created successfully with ID: {}", response.getOrderId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        log.info("Fetching order: {}", orderId);

        Order order = orderProcessingService.getOrderById(orderId);

        return ResponseEntity.ok(order);
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Order>> getCustomerOrders(@PathVariable String customerId) {
        log.info("Fetching orders for customer: {}", customerId);

        List<Order> orders = orderProcessingService.getOrdersByCustomerId(customerId);

        return ResponseEntity.ok(orders);
    }

    @PostMapping("/pricing")
    public ResponseEntity<OrderResponse> calculatePricing(@Valid @RequestBody OrderRequest request) {
        log.info("Calculating pricing for order request");

        OrderResponse response = orderProcessingService.calculatePricingOnly(request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return ResponseEntity.ok("Order Service is running");
    }
}
