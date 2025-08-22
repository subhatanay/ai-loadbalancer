package com.bits.inventory.model;

import com.bits.inventory.enums.AlertType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "low_stock_alerts", indexes = {
    @Index(name = "idx_alert_product_sku", columnList = "productSku"),
    @Index(name = "idx_alert_type", columnList = "alertType"),
    @Index(name = "idx_alert_resolved", columnList = "resolved"),
    @Index(name = "idx_alert_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockAlert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "alert_id", unique = true, nullable = false, length = 100)
    private String alertId;
    
    @Column(name = "product_sku", nullable = false, length = 100)
    private String productSku;
    
    @Column(name = "warehouse_location", nullable = false, length = 100)
    private String warehouseLocation;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;
    
    @Column(name = "current_quantity", nullable = false)
    private Integer currentQuantity;
    
    @Column(name = "threshold_quantity", nullable = false)
    private Integer thresholdQuantity;
    
    @Column(length = 500)
    private String message;
    
    @Column(nullable = false)
    private Boolean resolved = false;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Business methods
    public void resolve(String resolvedBy) {
        this.resolved = true;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = resolvedBy;
    }
    
    public boolean isActive() {
        return !resolved;
    }
}
