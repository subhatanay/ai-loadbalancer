package com.bits.notification.repository;

import com.bits.notification.model.NotificationDelivery;
import com.bits.notification.enums.DeliveryStatus;
import com.bits.notification.enums.NotificationChannel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    
    List<NotificationDelivery> findByNotificationId(Long notificationId);
    
    List<NotificationDelivery> findByChannel(NotificationChannel channel);
    
    List<NotificationDelivery> findByStatus(DeliveryStatus status);
    
    List<NotificationDelivery> findByChannelAndStatus(NotificationChannel channel, DeliveryStatus status);
    
    Optional<NotificationDelivery> findByProviderMessageId(String providerMessageId);
    
    @Query("SELECT d FROM NotificationDelivery d WHERE d.status = 'FAILED' AND d.retryCount < 3")
    List<NotificationDelivery> findFailedDeliveriesForRetry();
    
    // Additional methods for service compatibility
    long countByStatus(DeliveryStatus status);
    
    long countByChannel(NotificationChannel channel);
    
    @Query("SELECT d FROM NotificationDelivery d ORDER BY d.createdAt DESC LIMIT :limit")
    List<NotificationDelivery> findRecentDeliveries(@Param("limit") int limit);
    
    List<NotificationDelivery> findByCreatedAtBefore(LocalDateTime dateTime);
    
    boolean existsByNotificationIdAndChannelAndStatus(Long notificationId, NotificationChannel channel, DeliveryStatus status);
    
    // Additional ordering methods
    List<NotificationDelivery> findByNotificationIdOrderByCreatedAtDesc(Long notificationId);
    
    Page<NotificationDelivery> findByChannelOrderByCreatedAtDesc(NotificationChannel channel, Pageable pageable);
    
    Page<NotificationDelivery> findByStatusOrderByCreatedAtDesc(DeliveryStatus status, Pageable pageable);
    
    @Query("SELECT COUNT(d) FROM NotificationDelivery d WHERE d.channel = :channel AND d.status = :status AND d.createdAt >= :startDate")
    long countByChannelAndStatusAndCreatedAtAfter(@Param("channel") NotificationChannel channel, 
                                                 @Param("status") DeliveryStatus status, 
                                                 @Param("startDate") LocalDateTime startDate);
}
