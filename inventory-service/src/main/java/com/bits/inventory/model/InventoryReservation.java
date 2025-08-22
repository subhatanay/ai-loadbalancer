package com.bits.inventory.model;

import com.bits.inventory.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_reservations", indexes = {
    @Index(name = "idx_reservation_order_id", columnList = "orderId"),
    @Index(name = "idx_reservation_product_sku", columnList = "productSku"),
    @Index(name = "idx_reservation_status", columnList = "status"),
    @Index(name = "idx_reservation_expires_at", columnList = "expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "reservation_id", unique = true, nullable = false, length = 100)
    private String reservationId;
    
    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;
    
    @Column(name = "product_sku", nullable = false, length = 100)
    private String productSku;
    
    @Column(name = "warehouse_location", nullable = false, length = 100)
    private String warehouseLocation;
    
    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    // Business methods
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean isActive() {
        return status == ReservationStatus.ACTIVE && !isExpired();
    }
    
    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }
    
    public void release() {
        this.status = ReservationStatus.RELEASED;
    }
    
    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
    }
}
