package com.bits.order.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "order_number", unique = true, nullable = false)
    private String orderNumber;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "user_email", nullable = false)
    private String userEmail;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();
    
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(name = "total_items", nullable = false)
    private Integer totalItems;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;
    
    @Column(name = "payment_id")
    private String paymentId;
    
    @Column(name = "payment_status")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    
    @Embedded
    private ShippingAddress shippingAddress;
    
    @Column(name = "notes")
    private String notes;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "saga_id")
    private String sagaId; // For tracking distributed transaction
    
    @Column(name = "reservation_id")
    private String reservationId; // For tracking inventory reservation
    
    @Column(name = "jwt_token", length = 2000)
    private String jwtToken; // For saga operations authentication
    
    public void addOrderItem(OrderItem item) {
        item.setOrder(this);
        this.orderItems.add(item);
        calculateTotals();
    }
    
    public void updateStatus(OrderStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void updatePaymentStatus(PaymentStatus newPaymentStatus) {
        this.paymentStatus = newPaymentStatus;
        this.updatedAt = LocalDateTime.now();
    }
    
    private void calculateTotals() {
        this.totalAmount = orderItems.stream()
                .map(OrderItem::getSubtotal)
                .filter(Objects::nonNull)  // Filter out null subtotals
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.totalItems = orderItems.stream()
                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                .sum();
    }
}
