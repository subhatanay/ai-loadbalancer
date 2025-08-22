package com.bits.inventory.service;

import com.bits.inventory.enums.ReservationStatus;
import com.bits.inventory.model.InventoryItem;
import com.bits.inventory.model.InventoryReservation;
import com.bits.inventory.repository.InventoryItemRepository;
import com.bits.inventory.repository.InventoryReservationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReservationCleanupService {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ReservationCleanupService.class);
    
    @Autowired
    private InventoryReservationRepository reservationRepository;
    
    @Autowired
    private InventoryItemRepository inventoryItemRepository;
    
    @Autowired
    private InventoryEventPublisher eventPublisher;
    
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    @Transactional
    public void cleanupExpiredReservations() {
        logger.info("Starting cleanup of expired reservations");
        
        LocalDateTime now = LocalDateTime.now();
        List<InventoryReservation> expiredReservations = reservationRepository.findExpiredReservations(now);
        
        if (expiredReservations.isEmpty()) {
            logger.debug("No expired reservations found");
            return;
        }
        
        logger.info("Found {} expired reservations to cleanup", expiredReservations.size());
        
        for (InventoryReservation reservation : expiredReservations) {
            try {
                cleanupExpiredReservation(reservation);
            } catch (Exception e) {
                logger.error("Error cleaning up reservation: {}", reservation.getReservationId(), e);
            }
        }
        
        logger.info("Completed cleanup of expired reservations");
    }
    
    private void cleanupExpiredReservation(InventoryReservation reservation) {
        logger.debug("Cleaning up expired reservation: {}", reservation.getReservationId());
        
        // Find the inventory item
        Optional<InventoryItem> inventoryItemOpt = inventoryItemRepository
            .findByProductSkuAndWarehouseLocation(reservation.getProductSku(), reservation.getWarehouseLocation());
        
        if (inventoryItemOpt.isEmpty()) {
            logger.warn("Inventory item not found for expired reservation: {}", reservation.getReservationId());
            reservation.expire();
            reservationRepository.save(reservation);
            return;
        }
        
        InventoryItem item = inventoryItemOpt.get();
        
        // Release the reserved quantity back to available
        try {
            item.releaseReservation(reservation.getReservedQuantity());
            reservation.expire();
            
            // Save changes
            inventoryItemRepository.save(item);
            reservationRepository.save(reservation);
            
            // Publish event
            eventPublisher.publishInventoryReleasedEvent(reservation);
            
            logger.info("Successfully cleaned up expired reservation: {} for product: {} quantity: {}", 
                reservation.getReservationId(), reservation.getProductSku(), reservation.getReservedQuantity());
                
        } catch (Exception e) {
            logger.error("Error releasing reservation quantity for reservation: {}", 
                reservation.getReservationId(), e);
            
            // Mark as expired even if we couldn't release the quantity
            reservation.expire();
            reservationRepository.save(reservation);
        }
    }
    
    @Scheduled(cron = "0 0 2 * * ?") // Run daily at 2 AM
    @Transactional
    public void cleanupOldReservations() {
        logger.info("Starting cleanup of old reservation records");
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30); // Keep records for 30 days
        
        List<InventoryReservation> oldReservations = reservationRepository.findAll().stream()
            .filter(r -> r.getCreatedAt().isBefore(cutoffDate))
            .filter(r -> r.getStatus() == ReservationStatus.EXPIRED || r.getStatus() == ReservationStatus.CONFIRMED)
            .toList();
        
        if (!oldReservations.isEmpty()) {
            logger.info("Deleting {} old reservation records", oldReservations.size());
            reservationRepository.deleteAll(oldReservations);
        }
        
        logger.info("Completed cleanup of old reservation records");
    }
}
