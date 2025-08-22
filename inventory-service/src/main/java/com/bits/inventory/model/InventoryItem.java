package com.bits.inventory.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items", indexes = {
    @Index(name = "idx_inventory_product_sku", columnList = "productSku"),
    @Index(name = "idx_inventory_warehouse", columnList = "warehouseLocation"),
    @Index(name = "idx_inventory_available_quantity", columnList = "availableQuantity")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "product_sku", nullable = false, length = 100)
    private String productSku;
    
    @Column(name = "warehouse_location", nullable = false, length = 100)
    private String warehouseLocation;
    
    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;
    
    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;
    
    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;
    
    @Column(name = "minimum_stock_level", nullable = false)
    private Integer minimumStockLevel;
    
    @Column(name = "maximum_stock_level")
    private Integer maximumStockLevel;
    
    @Column(name = "reorder_point")
    private Integer reorderPoint;
    
    @Column(name = "reorder_quantity")
    private Integer reorderQuantity;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    // Business methods
    public boolean isLowStock() {
        return availableQuantity <= minimumStockLevel;
    }
    
    public boolean isOutOfStock() {
        return availableQuantity <= 0;
    }
    
    public boolean canReserve(Integer quantity) {
        return availableQuantity >= quantity;
    }
    
    public void reserveQuantity(Integer quantity) {
        if (!canReserve(quantity)) {
            throw new IllegalArgumentException("Insufficient available quantity");
        }
        this.availableQuantity -= quantity;
        this.reservedQuantity += quantity;
    }
    
    public void releaseReservation(Integer quantity) {
        if (reservedQuantity < quantity) {
            throw new IllegalArgumentException("Cannot release more than reserved quantity");
        }
        this.reservedQuantity -= quantity;
        this.availableQuantity += quantity;
    }
    
    public void confirmReservation(Integer quantity) {
        if (reservedQuantity < quantity) {
            throw new IllegalArgumentException("Cannot confirm more than reserved quantity");
        }
        this.reservedQuantity -= quantity;
        this.totalQuantity -= quantity;
    }
    
    public void adjustStock(Integer adjustment) {
        this.totalQuantity += adjustment;
        this.availableQuantity += adjustment;
    }
}
