package com.bits.inventory.repository;

import com.bits.inventory.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    
    Optional<InventoryItem> findByProductSku(String productSku);
    
    Optional<InventoryItem> findByProductSkuAndWarehouseLocation(String productSku, String warehouseLocation);
    
    List<InventoryItem> findByWarehouseLocation(String warehouseLocation);
    
    @Query("SELECT i FROM InventoryItem i WHERE i.availableQuantity <= i.minimumStockLevel")
    List<InventoryItem> findLowStockItems();
    
    @Query("SELECT i FROM InventoryItem i WHERE i.availableQuantity = 0")
    List<InventoryItem> findOutOfStockItems();
    
    @Query("SELECT i FROM InventoryItem i WHERE i.warehouseLocation = :warehouse AND i.availableQuantity <= i.minimumStockLevel")
    List<InventoryItem> findLowStockItemsByWarehouse(@Param("warehouse") String warehouseLocation);
    
    @Query("SELECT SUM(i.availableQuantity) FROM InventoryItem i WHERE i.productSku = :sku")
    Integer getTotalAvailableQuantityBySku(@Param("sku") String productSku);
    
    @Query("SELECT SUM(i.reservedQuantity) FROM InventoryItem i WHERE i.productSku = :sku")
    Integer getTotalReservedQuantityBySku(@Param("sku") String productSku);
    
    boolean existsByProductSku(String productSku);
    
    boolean existsByProductSkuAndWarehouseLocation(String productSku, String warehouseLocation);
}
