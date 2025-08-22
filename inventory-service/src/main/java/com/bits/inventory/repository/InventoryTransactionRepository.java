package com.bits.inventory.repository;

import com.bits.inventory.enums.MovementType;
import com.bits.inventory.model.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
    
    Optional<InventoryTransaction> findByTransactionId(String transactionId);
    
    List<InventoryTransaction> findByProductSku(String productSku);
    
    List<InventoryTransaction> findByMovementType(MovementType movementType);
    
    List<InventoryTransaction> findByReferenceId(String referenceId);
    
    @Query("SELECT t FROM InventoryTransaction t WHERE t.productSku = :sku AND t.createdAt BETWEEN :startDate AND :endDate")
    List<InventoryTransaction> findByProductSkuAndDateRange(
        @Param("sku") String productSku, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT t FROM InventoryTransaction t WHERE t.warehouseLocation = :warehouse AND t.createdAt BETWEEN :startDate AND :endDate")
    List<InventoryTransaction> findByWarehouseAndDateRange(
        @Param("warehouse") String warehouseLocation, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT t FROM InventoryTransaction t WHERE t.productSku = :sku ORDER BY t.createdAt DESC")
    List<InventoryTransaction> findByProductSkuOrderByCreatedAtDesc(@Param("sku") String productSku);
}
