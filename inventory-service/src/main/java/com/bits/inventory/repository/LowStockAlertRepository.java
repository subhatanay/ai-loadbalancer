package com.bits.inventory.repository;

import com.bits.inventory.enums.AlertType;
import com.bits.inventory.model.LowStockAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LowStockAlertRepository extends JpaRepository<LowStockAlert, Long> {
    
    Optional<LowStockAlert> findByAlertId(String alertId);
    
    List<LowStockAlert> findByProductSku(String productSku);
    
    List<LowStockAlert> findByAlertType(AlertType alertType);
    
    List<LowStockAlert> findByResolved(Boolean resolved);
    
    @Query("SELECT a FROM LowStockAlert a WHERE a.resolved = false")
    List<LowStockAlert> findActiveAlerts();
    
    @Query("SELECT a FROM LowStockAlert a WHERE a.productSku = :sku AND a.resolved = false")
    List<LowStockAlert> findActiveAlertsByProductSku(@Param("sku") String productSku);
    
    @Query("SELECT a FROM LowStockAlert a WHERE a.warehouseLocation = :warehouse AND a.resolved = false")
    List<LowStockAlert> findActiveAlertsByWarehouse(@Param("warehouse") String warehouseLocation);
    
    @Query("SELECT a FROM LowStockAlert a WHERE a.createdAt BETWEEN :startDate AND :endDate")
    List<LowStockAlert> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    boolean existsByProductSkuAndResolvedFalse(String productSku);
}
