package com.bits.order.event;

import com.bits.order.model.Order;
import com.bits.order.model.OrderStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {
    
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${kafka.topics.order-events:order-events}")
    private String orderEventsTopic;
    
    public void publishOrderCreatedEvent(Order order) {
        try {
            OrderCreatedEvent event = OrderCreatedEvent.builder()
                    .timestamp(LocalDateTime.now())
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .userId(order.getUserId())
                    .userEmail(order.getUserEmail())
                    .totalAmount(order.getTotalAmount())
                    .totalItems(order.getTotalItems())
                    .sagaId(order.getSagaId())
                    .items(order.getOrderItems().stream()
                            .map(item -> OrderCreatedEvent.OrderItemEvent.builder()
                                    .productId(item.getProductId())
                                    .productName(item.getProductName())
                                    .quantity(item.getQuantity())
                                    .price(item.getPrice())
                                    .subtotal(item.getSubtotal())
                                    .build())
                            .collect(Collectors.toList()))
                    .build();
            
            publishEvent(event, order.getOrderNumber());
            log.info("Published ORDER_CREATED event for order: {}", order.getOrderNumber());
            
        } catch (Exception e) {
            log.error("Failed to publish ORDER_CREATED event for order: {}", order.getOrderNumber(), e);
        }
    }
    
    public void publishOrderStatusUpdatedEvent(Order order, OrderStatus oldStatus) {
        try {
            OrderStatusUpdatedEvent event = OrderStatusUpdatedEvent.builder()
                    .timestamp(LocalDateTime.now())
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .userId(order.getUserId())
                    .oldStatus(oldStatus)
                    .newStatus(order.getStatus())
                    .build();
            
            publishEvent(event, order.getOrderNumber());
            log.info("Published ORDER_STATUS_UPDATED event for order: {} from {} to {}", 
                    order.getOrderNumber(), oldStatus, order.getStatus());
            
        } catch (Exception e) {
            log.error("Failed to publish ORDER_STATUS_UPDATED event for order: {}", order.getOrderNumber(), e);
        }
    }
    
    public void publishOrderCancelledEvent(Order order, String reason) {
        try {
            OrderCancelledEvent event = OrderCancelledEvent.builder()
                    .timestamp(LocalDateTime.now())
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .userId(order.getUserId())
                    .userEmail(order.getUserEmail())
                    .totalAmount(order.getTotalAmount())
                    .cancellationReason(reason)
                    .build();
            
            publishEvent(event, order.getOrderNumber());
            log.info("Published ORDER_CANCELLED event for order: {}", order.getOrderNumber());
            
        } catch (Exception e) {
            log.error("Failed to publish ORDER_CANCELLED event for order: {}", order.getOrderNumber(), e);
        }
    }
    
    // Saga-related events
    public void publishOrderInventoryReservedEvent(Order order) {
        publishSimpleEvent("ORDER_INVENTORY_RESERVED", order);
    }
    
    public void publishOrderInventoryFailedEvent(Order order) {
        publishSimpleEvent("ORDER_INVENTORY_FAILED", order);
    }
    
    public void publishOrderInventoryReleasedEvent(Order order) {
        publishSimpleEvent("ORDER_INVENTORY_RELEASED", order);
    }
    
    public void publishOrderPaymentCompletedEvent(Order order) {
        publishSimpleEvent("ORDER_PAYMENT_COMPLETED", order);
    }
    
    public void publishOrderPaymentFailedEvent(Order order) {
        publishSimpleEvent("ORDER_PAYMENT_FAILED", order);
    }
    
    public void publishOrderPaymentRefundedEvent(Order order) {
        publishSimpleEvent("ORDER_PAYMENT_REFUNDED", order);
    }
    
    public void publishOrderPaymentUpdatedEvent(Order order) {
        publishSimpleEvent("ORDER_PAYMENT_UPDATED", order);
    }
    
    public void publishOrderCartClearedEvent(Order order) {
        publishSimpleEvent("ORDER_CART_CLEARED", order);
    }
    
    public void publishOrderProcessingCompletedEvent(Order order) {
        publishSimpleEvent("ORDER_PROCESSING_COMPLETED", order);
    }
    
    public void publishOrderCancellationCompletedEvent(Order order) {
        publishSimpleEvent("ORDER_CANCELLATION_COMPLETED", order);
    }
    
    public void publishOrderCancellationFailedEvent(Order order, String reason) {
        publishSimpleEventWithReason("ORDER_CANCELLATION_FAILED", order, reason);
    }
    
    public void publishOrderSagaFailedEvent(Order order, String reason) {
        publishSimpleEventWithReason("ORDER_SAGA_FAILED", order, reason);
    }
    
    private void publishSimpleEvent(String eventType, Order order) {
        try {
            SimpleOrderEvent event = SimpleOrderEvent.builder()
                    .eventType(eventType)
                    .timestamp(LocalDateTime.now())
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .userId(order.getUserId())
                    .status(order.getStatus())
                    .paymentStatus(order.getPaymentStatus())
                    .build();
            
            publishEvent(event, order.getOrderNumber());
            log.info("Published {} event for order: {}", eventType, order.getOrderNumber());
            
        } catch (Exception e) {
            log.error("Failed to publish {} event for order: {}", eventType, order.getOrderNumber(), e);
        }
    }
    
    private void publishSimpleEventWithReason(String eventType, Order order, String reason) {
        try {
            SimpleOrderEventWithReason event = SimpleOrderEventWithReason.builder()
                    .eventType(eventType)
                    .timestamp(LocalDateTime.now())
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .userId(order.getUserId())
                    .status(order.getStatus())
                    .paymentStatus(order.getPaymentStatus())
                    .reason(reason)
                    .build();
            
            publishEvent(event, order.getOrderNumber());
            log.info("Published {} event for order: {} with reason: {}", eventType, order.getOrderNumber(), reason);
            
        } catch (Exception e) {
            log.error("Failed to publish {} event for order: {}", eventType, order.getOrderNumber(), e);
        }
    }
    
    private void publishEvent(Object event, String key) throws JsonProcessingException {
        String eventJson = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(orderEventsTopic, key, eventJson);
    }
}
