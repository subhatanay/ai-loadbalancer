package com.bits.notification.repository;

import com.bits.notification.model.Notification;
import com.bits.notification.enums.NotificationStatus;
import com.bits.notification.enums.NotificationType;
import com.bits.notification.enums.NotificationPriority;
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
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    Optional<Notification> findByNotificationId(String notificationId);
    
    Page<Notification> findByUserId(String userId, Pageable pageable);
    
    Page<Notification> findByUserIdAndType(String userId, NotificationType type, Pageable pageable);
    
    List<Notification> findByStatus(NotificationStatus status);
    
    List<Notification> findByStatusAndScheduledAtBefore(NotificationStatus status, LocalDateTime time);
    
    List<Notification> findByStatusAndExpiresAtBefore(NotificationStatus status, LocalDateTime time);
    
    List<Notification> findByPriorityAndStatus(NotificationPriority priority, NotificationStatus status);
    
    @Query("SELECT n FROM Notification n WHERE n.status = 'FAILED' AND n.retryCount < n.maxRetries")
    List<Notification> findFailedNotificationsForRetry();
    
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.status IN :statuses ORDER BY n.createdAt DESC")
    Page<Notification> findByUserIdAndStatusIn(@Param("userId") String userId, 
                                              @Param("statuses") List<NotificationStatus> statuses, 
                                              Pageable pageable);
    
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.type = :type AND n.createdAt >= :startDate")
    long countByUserIdAndTypeAndCreatedAtAfter(@Param("userId") String userId, 
                                              @Param("type") NotificationType type, 
                                              @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT n FROM Notification n WHERE n.scheduledAt IS NOT NULL AND n.scheduledAt <= :currentTime AND n.status = 'PENDING'")
    List<Notification> findScheduledNotificationsDueForProcessing(@Param("currentTime") LocalDateTime currentTime);
    
    boolean existsByNotificationId(String notificationId);
    
    // Additional methods for service compatibility
    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    Page<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status, Pageable pageable);
    
    @Query("SELECT n FROM Notification n WHERE n.scheduledAt IS NOT NULL AND n.scheduledAt <= :currentTime AND n.status = 'PENDING'")
    List<Notification> findScheduledNotifications(@Param("currentTime") LocalDateTime currentTime);
    
    @Query("SELECT n FROM Notification n WHERE n.status = 'FAILED' AND n.retryCount < n.maxRetries")
    List<Notification> findRetryableFailedNotifications();
    
    long countByUserId(String userId);
    
    long countByUserIdAndStatus(String userId, NotificationStatus status);
    
    long countByStatus(NotificationStatus status);
    
    @Query("SELECT n FROM Notification n WHERE n.expiresAt IS NOT NULL AND n.expiresAt <= :currentTime AND n.status NOT IN ('EXPIRED', 'CANCELLED')")
    List<Notification> findExpiredNotifications(@Param("currentTime") LocalDateTime currentTime);
    
    List<Notification> findByCreatedAtBeforeAndStatusIn(LocalDateTime cutoffDate, List<NotificationStatus> statuses);
}
