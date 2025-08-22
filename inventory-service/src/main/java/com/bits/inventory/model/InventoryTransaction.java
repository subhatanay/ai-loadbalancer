package com.bits.inventory.model;

import com.bits.inventory.enums.MovementType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_transactions", indexes = {
    @Index(name = "idx_transaction_product_sku", columnList = "productSku"),
    @Index(name = "idx_transaction_type", columnList = "movementType"),
    @Index(name = "idx_transaction_reference", columnList = "referenceId"),
    @Index(name = "idx_transaction_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "transaction_id", unique = true, nullable = false, length = 100)
    private String transactionId;
    
    @Column(name = "product_sku", nullable = false, length = 100)
    private String productSku;
    
    @Column(name = "warehouse_location", nullable = false, length = 100)
    private String warehouseLocation;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private MovementType movementType;
    
    @Column(name = "quantity_change", nullable = false)
    private Integer quantityChange;
    
    @Column(name = "previous_quantity")
    private Integer previousQuantity;
    
    @Column(name = "new_quantity")
    private Integer newQuantity;
    
    @Column(name = "reference_id", length = 100)
    private String referenceId;
    
    @Column(name = "reference_type", length = 50)
    private String referenceType;
    
    @Column(length = 500)
    private String notes;
    
    @Column(name = "performed_by", length = 100)
    private String performedBy;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
