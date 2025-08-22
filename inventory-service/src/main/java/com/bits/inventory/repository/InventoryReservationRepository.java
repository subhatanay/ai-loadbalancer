package com.bits.inventory.repository;

import com.bits.inventory.enums.ReservationStatus;
import com.bits.inventory.model.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    
    Optional<InventoryReservation> findByReservationId(String reservationId);
    
    List<InventoryReservation> findByOrderId(String orderId);
    
    List<InventoryReservation> findByProductSku(String productSku);
    
    List<InventoryReservation> findByStatus(ReservationStatus status);
    
    @Query("SELECT r FROM InventoryReservation r WHERE r.status = 'ACTIVE' AND r.expiresAt < :now")
    List<InventoryReservation> findExpiredReservations(@Param("now") LocalDateTime now);
    
    @Query("SELECT r FROM InventoryReservation r WHERE r.productSku = :sku AND r.status = :status")
    List<InventoryReservation> findByProductSkuAndStatus(@Param("sku") String productSku, @Param("status") ReservationStatus status);
    
    @Query("SELECT SUM(r.reservedQuantity) FROM InventoryReservation r WHERE r.productSku = :sku AND r.status = 'ACTIVE'")
    Integer getTotalActiveReservationsBySku(@Param("sku") String productSku);
    
    @Query("SELECT r FROM InventoryReservation r WHERE r.orderId = :orderId AND r.productSku = :sku")
    Optional<InventoryReservation> findByOrderIdAndProductSku(@Param("orderId") String orderId, @Param("sku") String productSku);
    
    boolean existsByReservationId(String reservationId);
}
