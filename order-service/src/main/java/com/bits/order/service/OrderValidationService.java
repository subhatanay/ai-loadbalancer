package com.bits.order.service;

import com.bits.order.dto.CreateOrderRequest;
import com.bits.order.dto.OrderItemRequest;
import com.bits.order.exception.OrderValidationException;
import com.bits.order.model.Order;
import com.bits.order.model.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class OrderValidationService {
    
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("10000.00");
    private static final int MAX_ITEMS_PER_ORDER = 50;
    
    public void validateCreateOrderRequest(CreateOrderRequest request) {
        if (request == null) {
            throw new OrderValidationException("Order request cannot be null");
        }
        
        // Items validation is optional since items come from cart
        // If items are provided in request, validate them
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            if (request.getItems().size() > MAX_ITEMS_PER_ORDER) {
                throw new OrderValidationException("Order cannot contain more than " + MAX_ITEMS_PER_ORDER + " items");
            }
            
            // Validate each item
            for (OrderItemRequest item : request.getItems()) {
                validateOrderItem(item);
            }
            
            // Validate total amount
            BigDecimal totalAmount = calculateTotalAmount(request.getItems());
            if (totalAmount.compareTo(MAX_ORDER_AMOUNT) > 0) {
                throw new OrderValidationException("Order total cannot exceed $" + MAX_ORDER_AMOUNT);
            }
        }
        
        // Validate shipping address
        if (request.getShippingAddress() == null) {
            throw new OrderValidationException("Shipping address is required");
        }
    }
    
    public void validateOrderItem(OrderItemRequest item) {
        if (item.getProductId() == null || item.getProductId().trim().isEmpty()) {
            throw new OrderValidationException("Product ID cannot be empty");
        }
        
        if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new OrderValidationException("Product price must be greater than 0");
        }
        
        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            throw new OrderValidationException("Product quantity must be greater than 0");
        }
        
        if (item.getQuantity() > 100) {
            throw new OrderValidationException("Product quantity cannot exceed 100");
        }
    }
    
    public void validateStatusTransition(OrderStatus currentStatus, OrderStatus newStatus) {
        if (currentStatus == newStatus) {
            throw new OrderValidationException("Order is already in " + newStatus + " status");
        }
        
        List<OrderStatus> validTransitions = getValidTransitions(currentStatus);
        if (!validTransitions.contains(newStatus)) {
            throw new OrderValidationException(
                String.format("Invalid status transition from %s to %s", currentStatus, newStatus)
            );
        }
    }
    
    public void validateOrderCancellation(Order order) {
        List<OrderStatus> cancellableStatuses = Arrays.asList(
            OrderStatus.PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PAYMENT_PROCESSING
        );
        
        if (!cancellableStatuses.contains(order.getStatus())) {
            throw new OrderValidationException(
                "Order cannot be cancelled in " + order.getStatus() + " status"
            );
        }
    }
    
    private List<OrderStatus> getValidTransitions(OrderStatus currentStatus) {
        return switch (currentStatus) {
            case PENDING -> Arrays.asList(
                OrderStatus.CONFIRMED, 
                OrderStatus.CANCELLED
            );
            case CONFIRMED -> Arrays.asList(
                OrderStatus.PAYMENT_PROCESSING, 
                OrderStatus.CANCELLED
            );
            case PAYMENT_PROCESSING -> Arrays.asList(
                OrderStatus.PAYMENT_COMPLETED, 
                OrderStatus.PAYMENT_FAILED,
                OrderStatus.CANCELLED
            );
            case PAYMENT_COMPLETED -> Arrays.asList(
                OrderStatus.INVENTORY_RESERVED,
                OrderStatus.INVENTORY_FAILED
            );
            case PAYMENT_FAILED -> Arrays.asList(
                OrderStatus.CANCELLED,
                OrderStatus.PAYMENT_PROCESSING
            );
            case INVENTORY_RESERVED -> Arrays.asList(
                OrderStatus.PROCESSING
            );
            case INVENTORY_FAILED -> Arrays.asList(
                OrderStatus.CANCELLED,
                OrderStatus.REFUNDED
            );
            case PROCESSING -> Arrays.asList(
                OrderStatus.SHIPPED,
                OrderStatus.CANCELLED
            );
            case SHIPPED -> Arrays.asList(
                OrderStatus.DELIVERED
            );
            case DELIVERED -> Arrays.asList(
                OrderStatus.REFUNDED
            );
            case CANCELLED, REFUNDED -> Arrays.asList(); // Terminal states
        };
    }
    
    private BigDecimal calculateTotalAmount(List<OrderItemRequest> items) {
        return items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
