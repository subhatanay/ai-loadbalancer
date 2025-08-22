package com.bits.order.saga;

import com.bits.order.client.CartServiceClient;
import com.bits.order.client.InventoryServiceClient;
import com.bits.order.client.NotificationServiceClient;
import com.bits.order.client.PaymentServiceClient;
import com.bits.order.dto.InventoryReservationRequest;
import com.bits.order.dto.PaymentRequest;
import com.bits.order.dto.ReservationResponseDto;
import com.bits.order.event.OrderEventPublisher;
import com.bits.order.model.Order;
import com.bits.order.model.OrderItem;
import com.bits.order.model.OrderStatus;
import com.bits.order.model.PaymentStatus;

import java.util.ArrayList;
import java.util.List;
import com.bits.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {
    
    private final OrderRepository orderRepository;
    private final InventoryServiceClient inventoryClient;
    private final PaymentServiceClient paymentClient;
    private final CartServiceClient cartClient;
    private final OrderEventPublisher eventPublisher;
    private final NotificationServiceClient notificationClient;
    
    @Async
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<Void> startOrderProcessingSaga(Order order) {
        log.info("Starting order processing saga for order: {}", order.getOrderNumber());
        
        try {
            // Step 1: Reserve inventory
            boolean inventoryReserved = reserveInventory(order);
            if (!inventoryReserved) {
                handleSagaFailure(order, "Inventory reservation failed");
                return CompletableFuture.completedFuture(null);
            }
            
            // Step 2: Process payment
            boolean paymentProcessed = processPayment(order);
            if (!paymentProcessed) {
                // Compensate: Release inventory
                releaseInventory(order);
                handleSagaFailure(order, "Payment processing failed");
                return CompletableFuture.completedFuture(null);
            }
            
            // Step 3: Clear cart
            clearUserCart(order);
            
            // Step 4: Update order status to processing
            updateOrderStatus(order, OrderStatus.PROCESSING);
            
            log.info("Order processing saga completed successfully for order: {}", order.getOrderNumber());
            eventPublisher.publishOrderProcessingCompletedEvent(order);
            
        } catch (Exception e) {
            log.error("Error in order processing saga for order: {}", order.getOrderNumber(), e);
            handleSagaFailure(order, "Saga execution failed: " + e.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    @Async
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public CompletableFuture<Void> startOrderCancellationSaga(Order order) {
        log.info("Starting order cancellation saga for order: {}", order.getOrderNumber());
        
        try {
            // Step 1: Refund payment if payment was completed
            if (order.getPaymentStatus() == PaymentStatus.COMPLETED) {
                refundPayment(order);
            }
            
            // Step 2: Release inventory if it was reserved
            if (order.getStatus() == OrderStatus.INVENTORY_RESERVED || 
                order.getStatus() == OrderStatus.PROCESSING) {
                releaseInventory(order);
            }
            
            log.info("Order cancellation saga completed for order: {}", order.getOrderNumber());
            eventPublisher.publishOrderCancellationCompletedEvent(order);
            
        } catch (Exception e) {
            log.error("Error in order cancellation saga for order: {}", order.getOrderNumber(), e);
            // For cancellation saga, we still mark as cancelled but log the compensation failures
            eventPublisher.publishOrderCancellationFailedEvent(order, e.getMessage());
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    private boolean reserveInventory(Order order) {
        try {
            log.info("Reserving inventory for order: {}", order.getOrderNumber());
            
            // Reserve inventory for each product individually
            List<String> reservationIds = new ArrayList<>();
            boolean allReservationsSuccessful = true;
            
            for (OrderItem item : order.getOrderItems()) {
                InventoryReservationRequest request = InventoryReservationRequest.builder()
                        .orderId(order.getId().toString())
                        .productSku(item.getProductId())
                        .quantity(item.getQuantity())
                        .reservationDurationMinutes(30) // Default 30 minutes
                        .build();
                
                ReservationResponseDto reservationResponse = inventoryClient.reserveInventory(request, order.getJwtToken());
                
                if (reservationResponse != null && Boolean.TRUE.equals(reservationResponse.getSuccess())) {
                    reservationIds.add(reservationResponse.getReservationId());
                    log.info("Successfully reserved inventory for product {} in order {}", 
                            item.getProductId(), order.getOrderNumber());
                } else {
                    log.error("Failed to reserve inventory for product {} in order {}", 
                            item.getProductId(), order.getOrderNumber());
                    allReservationsSuccessful = false;
                    
                    // Release any successful reservations before failing
                    for (String reservationId : reservationIds) {
                        inventoryClient.releaseInventory(reservationId, order.getJwtToken());
                    }
                    break;
                }
            }
            
            if (allReservationsSuccessful) {
                // Store all reservation IDs in order (comma-separated for simplicity)
                order.setReservationId(String.join(",", reservationIds));
                orderRepository.save(order);
                
                updateOrderStatus(order, OrderStatus.INVENTORY_RESERVED);
                eventPublisher.publishOrderInventoryReservedEvent(order);
                return true;
            } else {
                updateOrderStatus(order, OrderStatus.INVENTORY_FAILED);
                eventPublisher.publishOrderInventoryFailedEvent(order);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Failed to reserve inventory for order: {}", order.getOrderNumber(), e);
            updateOrderStatus(order, OrderStatus.INVENTORY_FAILED);
            return false;
        }
    }
    
    private boolean processPayment(Order order) {
        try {
            log.info("Processing payment for order: {}", order.getOrderNumber());
            
            updateOrderStatus(order, OrderStatus.PAYMENT_PROCESSING);
            updatePaymentStatus(order, PaymentStatus.PROCESSING);
            
            PaymentRequest request = PaymentRequest.builder()
                    .orderId(order.getId())
                    .orderNumber(order.getOrderNumber())
                    .userId(order.getUserId())
                    .amount(order.getTotalAmount())
                    .currency("USD")
                    .build();
            
            String paymentId = paymentClient.processPayment(request, order.getJwtToken());
            
            if (paymentId != null) {
                order.setPaymentId(paymentId);
                updateOrderStatus(order, OrderStatus.PAYMENT_COMPLETED);
                updatePaymentStatus(order, PaymentStatus.COMPLETED);
                eventPublisher.publishOrderPaymentCompletedEvent(order);
                
                // Send payment confirmation notification
                try {
                    notificationClient.sendPaymentConfirmation(
                        order.getUserId(),
                        order.getOrderNumber(),
                        order.getUserEmail(),
                        paymentId,
                        order.getJwtToken() // Use JWT token from order
                    );
                    log.info("Payment confirmation notification sent for order: {}", order.getOrderNumber());
                } catch (Exception e) {
                    log.warn("Failed to send payment confirmation notification for order: {}", order.getOrderNumber(), e);
                }
                
                return true;
            } else {
                updateOrderStatus(order, OrderStatus.PAYMENT_FAILED);
                updatePaymentStatus(order, PaymentStatus.FAILED);
                eventPublisher.publishOrderPaymentFailedEvent(order);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Failed to process payment for order: {}", order.getOrderNumber(), e);
            updateOrderStatus(order, OrderStatus.PAYMENT_FAILED);
            updatePaymentStatus(order, PaymentStatus.FAILED);
            return false;
        }
    }
    
    private void clearUserCart(Order order) {
        try {
            log.info("Clearing cart for user: {} after order: {}", order.getUserId(), order.getOrderNumber());
            String jwtToken = order.getJwtToken();
            if (jwtToken == null || jwtToken.isEmpty()) {
                log.error("No JWT token available for cart clearing - order: {}", order.getOrderNumber());
                throw new RuntimeException("JWT token required for cart operations");
            }
            cartClient.clearCart(jwtToken);
            eventPublisher.publishOrderCartClearedEvent(order);
        } catch (Exception e) {
            log.error("Failed to clear cart for user: {} after order: {}", order.getUserId(), order.getOrderNumber(), e);
            // This is not critical for order processing, so we don't fail the saga
        }
    }
    
    private void releaseInventory(Order order) {
        try {
            log.info("Releasing inventory for order: {}", order.getOrderNumber());
            
            if (order.getReservationId() != null && !order.getReservationId().isEmpty()) {
                // Handle multiple reservation IDs (comma-separated)
                String[] reservationIds = order.getReservationId().split(",");
                boolean allReleased = true;
                
                for (String reservationId : reservationIds) {
                    reservationId = reservationId.trim();
                    if (!reservationId.isEmpty()) {
                        boolean released = inventoryClient.releaseInventory(reservationId, order.getJwtToken());
                        if (released) {
                            log.info("Successfully released inventory reservation: {} for order: {}", 
                                    reservationId, order.getOrderNumber());
                        } else {
                            log.warn("Failed to release inventory reservation: {} for order: {}", 
                                    reservationId, order.getOrderNumber());
                            allReleased = false;
                        }
                    }
                }
                
                if (allReleased) {
                    eventPublisher.publishOrderInventoryReleasedEvent(order);
                }
            } else {
                log.warn("No reservation ID found for order: {}, skipping inventory release", order.getOrderNumber());
            }
        } catch (Exception e) {
            log.error("Failed to release inventory for order: {}", order.getOrderNumber(), e);
            // Log but don't throw - this is compensation logic
        }
    }
    
    private void refundPayment(Order order) {
        try {
            log.info("Refunding payment for order: {}", order.getOrderNumber());
            paymentClient.refundPayment(order.getPaymentId(), order.getJwtToken());
            updatePaymentStatus(order, PaymentStatus.REFUNDED);
            eventPublisher.publishOrderPaymentRefundedEvent(order);
        } catch (Exception e) {
            log.error("Failed to refund payment for order: {}", order.getOrderNumber(), e);
            // Log but don't throw - this is compensation logic
        }
    }
    
    @Transactional
    private void updateOrderStatus(Order order, OrderStatus status) {
        OrderStatus previousStatus = order.getStatus();
        order.updateStatus(status);
        orderRepository.save(order);
        
        // Send status update notifications for important status changes
        if (shouldNotifyStatusChange(previousStatus, status)) {
            try {
                notificationClient.sendOrderStatusUpdate(
                    order.getUserId(),
                    order.getOrderNumber(),
                    order.getUserEmail(),
                    status.toString(),
                    order.getJwtToken() // Use JWT token from order
                );
                log.info("Order status update notification sent for order: {} - Status: {}", 
                        order.getOrderNumber(), status);
            } catch (Exception e) {
                log.warn("Failed to send order status update notification for order: {} - Status: {}", 
                        order.getOrderNumber(), status, e);
            }
        }
    }
    
    private boolean shouldNotifyStatusChange(OrderStatus previousStatus, OrderStatus newStatus) {
        // Only notify for significant status changes that customers care about
        return newStatus == OrderStatus.PROCESSING || 
               newStatus == OrderStatus.PAYMENT_COMPLETED ||
               newStatus == OrderStatus.CANCELLED ||
               newStatus == OrderStatus.PAYMENT_FAILED;
    }
    
    @Transactional
    private void updatePaymentStatus(Order order, PaymentStatus paymentStatus) {
        order.updatePaymentStatus(paymentStatus);
        orderRepository.save(order);
    }
    
    private void handleSagaFailure(Order order, String reason) {
        log.error("Saga failed for order: {} - {}", order.getOrderNumber(), reason);
        updateOrderStatus(order, OrderStatus.CANCELLED);
        eventPublisher.publishOrderSagaFailedEvent(order, reason);
    }
}
