package com.bits.notification.repository;

import com.bits.notification.model.NotificationTemplate;
import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationType;
import com.bits.notification.enums.TemplateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    
    Optional<NotificationTemplate> findByTemplateId(String templateId);
    
    List<NotificationTemplate> findByNotificationType(NotificationType notificationType);
    
    List<NotificationTemplate> findByChannel(NotificationChannel channel);
    
    List<NotificationTemplate> findByNotificationTypeAndChannel(NotificationType notificationType, 
                                                              NotificationChannel channel);
    
    List<NotificationTemplate> findByActiveTrue();
    
    Page<NotificationTemplate> findByNameContainingIgnoreCase(String name, Pageable pageable);
    
    @Query("SELECT t FROM NotificationTemplate t WHERE t.notificationType = :type AND t.channel = :channel AND t.active = true ORDER BY t.version DESC")
    Optional<NotificationTemplate> findLatestActiveTemplate(@Param("type") NotificationType type, 
                                                           @Param("channel") NotificationChannel channel);
    
    boolean existsByTemplateId(String templateId);
    
    boolean existsByNameAndNotificationTypeAndChannel(String name, NotificationType notificationType, NotificationChannel channel);
    
    // Additional methods for service compatibility
    Optional<NotificationTemplate> findByNameAndChannelAndActiveTrue(String name, NotificationChannel channel);
    
    Page<NotificationTemplate> findByTypeAndActiveTrueOrderByCreatedAtDesc(TemplateType type, Pageable pageable);
    
    Page<NotificationTemplate> findByChannelAndActiveTrueOrderByCreatedAtDesc(NotificationChannel channel, Pageable pageable);
    
    List<NotificationTemplate> findByActiveTrueOrderByCreatedAtDesc();
}
