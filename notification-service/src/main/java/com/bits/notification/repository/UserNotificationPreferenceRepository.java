package com.bits.notification.repository;

import com.bits.notification.model.UserNotificationPreference;
import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, Long> {
    
    List<UserNotificationPreference> findByUserId(String userId);
    
    List<UserNotificationPreference> findByUserIdAndNotificationType(String userId, NotificationType notificationType);
    
    Optional<UserNotificationPreference> findByUserIdAndNotificationTypeAndChannel(String userId, 
                                                                                  NotificationType notificationType, 
                                                                                  NotificationChannel channel);
    
    List<UserNotificationPreference> findByUserIdAndEnabledTrue(String userId);
    
    @Query("SELECT p FROM UserNotificationPreference p WHERE p.userId = :userId AND p.notificationType = :type AND p.enabled = true")
    List<UserNotificationPreference> findEnabledPreferences(@Param("userId") String userId, 
                                                           @Param("type") NotificationType type);
    
    // Alias method for service compatibility
    default Optional<UserNotificationPreference> findByUserIdAndTypeAndChannel(String userId, 
                                                                              NotificationType type, 
                                                                              NotificationChannel channel) {
        return findByUserIdAndNotificationTypeAndChannel(userId, type, channel);
    }
    
    // Additional alias methods
    default List<UserNotificationPreference> findByUserIdAndType(String userId, NotificationType type) {
        return findByUserIdAndNotificationType(userId, type);
    }
    
    default List<UserNotificationPreference> findByUserIdAndChannel(String userId, NotificationChannel channel) {
        return findByUserIdAndNotificationTypeAndChannel(userId, null, channel)
            .map(List::of)
            .orElse(List.of());
    }
    
    boolean existsByUserIdAndNotificationTypeAndChannel(String userId, NotificationType notificationType, NotificationChannel channel);
}
