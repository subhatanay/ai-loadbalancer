package com.bits.notification.service;

import com.bits.notification.dto.*;
import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationStatus;
import com.bits.notification.exception.NotificationNotFoundException;
import com.bits.notification.exception.InvalidNotificationStateException;
import com.bits.notification.model.Notification;
import com.bits.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @Autowired
    private NotificationValidationService validationService;
    
    @Autowired
    private NotificationEventPublisher eventPublisher;
    
    @Transactional
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        logger.info("Creating notification for user: {}", request.getUserId());
        
        // Validate request
        validationService.validateCreateRequest(request);
        
        // Create notification entity
        Notification notification = new Notification();
        notification.setNotificationId(UUID.randomUUID().toString()); // Generate unique notification ID
        notification.setUserId(request.getUserId());
        notification.setUserEmail(request.getUserEmail());
        notification.setUserPhone(request.getUserPhone());
        notification.setType(request.getType());
        notification.setTitle(request.getTitle());
        notification.setMessage(request.getMessage());
        notification.setPriority(request.getPriority());
        notification.setChannels(request.getChannels());
        notification.setMetadata(request.getMetadata());
        notification.setTemplateId(request.getTemplateId());
        notification.setScheduledAt(request.getScheduledAt());
        notification.setExpiresAt(request.getExpiresAt());
        if (request.getMaxRetries() != null) {
            notification.setMaxRetries(request.getMaxRetries());
        }
        notification.setStatus(NotificationStatus.PENDING);
        
        // Save notification
        notification = notificationRepository.save(notification);
        
        // Publish event
        eventPublisher.publishNotificationCreated(notification);
        
        logger.info("Created notification with ID: {}", notification.getId());
        return mapToResponse(notification);
    }
    
    @Cacheable(value = "notifications", key = "#id")
    @Transactional(readOnly = true)
    public NotificationResponse getNotification(Long id) {
        logger.debug("Fetching notification with ID: {}", id);
        
        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new NotificationNotFoundException("Notification not found with ID: " + id));
        
        return mapToResponse(notification);
    }
    
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(String userId, Pageable pageable) {
        logger.debug("Fetching notifications for user: {}", userId);
        
        Page<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return notifications.map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotificationsByStatus(NotificationStatus status, Pageable pageable) {
        logger.debug("Fetching notifications with status: {}", status);
        
        Page<Notification> notifications = notificationRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return notifications.map(this::mapToResponse);
    }
    
    @CacheEvict(value = "notifications", key = "#id")
    @Transactional
    public NotificationResponse updateNotificationStatus(Long id, UpdateNotificationRequest request) {
        logger.info("Updating notification status for ID: {}", id);
        
        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new NotificationNotFoundException("Notification not found with ID: " + id));
        
        // Validate status transition
        validationService.validateStatusTransition(notification.getStatus(), request.getStatus());
        
        NotificationStatus oldStatus = notification.getStatus();
        notification.setStatus(request.getStatus());
        notification.setUpdatedAt(LocalDateTime.now());
        
        notification = notificationRepository.save(notification);
        
        // Publish event
        eventPublisher.publishNotificationUpdated(notification, oldStatus);
        
        logger.info("Updated notification status from {} to {} for ID: {}", oldStatus, request.getStatus(), id);
        return mapToResponse(notification);
    }
    
    @CacheEvict(value = "notifications", key = "#id")
    @Transactional
    public void cancelNotification(Long id, String reason) {
        logger.info("Cancelling notification with ID: {}", id);
        
        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new NotificationNotFoundException("Notification not found with ID: " + id));
        
        if (notification.getStatus() == NotificationStatus.SENT || 
            notification.getStatus() == NotificationStatus.DELIVERED) {
            throw new InvalidNotificationStateException("Cannot cancel notification that has already been sent or delivered");
        }
        
        notification.setStatus(NotificationStatus.CANCELLED);
        notification.setUpdatedAt(LocalDateTime.now());
        
        notificationRepository.save(notification);
        
        // Publish event
        eventPublisher.publishNotificationCancelled(notification, reason);
        
        logger.info("Cancelled notification with ID: {}", id);
    }
    
    @Transactional(readOnly = true)
    public List<NotificationResponse> getScheduledNotifications() {
        logger.debug("Fetching scheduled notifications");
        
        List<Notification> notifications = notificationRepository.findScheduledNotifications(LocalDateTime.now());
        return notifications.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<NotificationResponse> getRetryableNotifications() {
        logger.debug("Fetching retryable notifications");
        
        List<Notification> notifications = notificationRepository.findRetryableFailedNotifications();
        return notifications.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public NotificationStatsResponse getNotificationStats(String userId) {
        logger.debug("Fetching notification stats for user: {}", userId);
        
        long totalCount = notificationRepository.countByUserId(userId);
        long sentCount = notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.SENT);
        long deliveredCount = notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.DELIVERED);
        long failedCount = notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.FAILED);
        long pendingCount = notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.PENDING);
        
        NotificationStatsResponse stats = new NotificationStatsResponse();
        stats.setTotalCount(totalCount);
        stats.setSentCount(sentCount);
        stats.setDeliveredCount(deliveredCount);
        stats.setFailedCount(failedCount);
        stats.setPendingCount(pendingCount);
        
        return stats;
    }
    
    @Transactional
    public void processExpiredNotifications() {
        logger.info("Processing expired notifications");
        
        List<Notification> expiredNotifications = notificationRepository.findExpiredNotifications(LocalDateTime.now());
        
        for (Notification notification : expiredNotifications) {
            notification.setStatus(NotificationStatus.EXPIRED);
            notification.setUpdatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
            
            // Publish event
            eventPublisher.publishNotificationExpired(notification);
        }
        
        logger.info("Processed {} expired notifications", expiredNotifications.size());
    }
    
    private NotificationResponse mapToResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setId(notification.getId());
        response.setUserId(notification.getUserId());
        response.setType(notification.getType());
        response.setTitle(notification.getTitle());
        response.setContent(notification.getContent());
        response.setPriority(notification.getPriority());
        response.setChannels(notification.getChannels());
        response.setMetadata(notification.getMetadata());
        response.setStatus(notification.getStatus());
        response.setRetryCount(notification.getRetryCount());
        response.setMaxRetries(notification.getMaxRetries());
        response.setScheduledAt(notification.getScheduledAt());
        response.setExpiresAt(notification.getExpiresAt());
        response.setCreatedAt(notification.getCreatedAt());
        response.setUpdatedAt(notification.getUpdatedAt());
        response.setSentAt(notification.getSentAt());
        response.setDeliveredAt(notification.getDeliveredAt());
        response.setVersion(notification.getVersion());
        
        return response;
    }
}
