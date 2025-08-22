package com.bits.notification.service;

import com.bits.notification.enums.NotificationStatus;
import com.bits.notification.model.Notification;
import com.bits.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationSchedulerService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationSchedulerService.class);
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private NotificationProcessingService processingService;
    
    /**
     * Process scheduled notifications every minute
     */
    @Scheduled(fixedRate = 60000) // Every 60 seconds
    @Transactional
    public void processScheduledNotifications() {
        logger.debug("Processing scheduled notifications");
        
        try {
            List<Notification> scheduledNotifications = notificationRepository.findScheduledNotifications(LocalDateTime.now());
            
            if (!scheduledNotifications.isEmpty()) {
                logger.info("Found {} scheduled notifications to process", scheduledNotifications.size());
                
                for (Notification notification : scheduledNotifications) {
                    try {
                        // Update status to processing
                        notification.setStatus(NotificationStatus.PROCESSING);
                        notification.setUpdatedAt(LocalDateTime.now());
                        notificationRepository.save(notification);
                        
                        // Process the notification
                        processingService.processNotification(notification);
                        
                        logger.debug("Processed scheduled notification: {}", notification.getId());
                        
                    } catch (Exception e) {
                        logger.error("Failed to process scheduled notification: {}", notification.getId(), e);
                        
                        // Mark as failed
                        notification.setStatus(NotificationStatus.FAILED);
                        notification.setUpdatedAt(LocalDateTime.now());
                        notificationRepository.save(notification);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in scheduled notification processing", e);
        }
    }
    
    /**
     * Process expired notifications every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void processExpiredNotifications() {
        logger.debug("Processing expired notifications");
        
        try {
            notificationService.processExpiredNotifications();
            
        } catch (Exception e) {
            logger.error("Error in expired notification processing", e);
        }
    }
    
    /**
     * Retry failed notifications every 10 minutes
     */
    @Scheduled(fixedRate = 600000) // Every 10 minutes
    @Transactional
    public void retryFailedNotifications() {
        logger.debug("Retrying failed notifications");
        
        try {
            List<Notification> retryableNotifications = notificationRepository.findRetryableFailedNotifications();
            
            if (!retryableNotifications.isEmpty()) {
                logger.info("Found {} notifications to retry", retryableNotifications.size());
                
                for (Notification notification : retryableNotifications) {
                    try {
                        // Check if retry is allowed
                        if (notification.getRetryCount() < notification.getMaxRetries()) {
                            
                            // Increment retry count
                            notification.setRetryCount(notification.getRetryCount() + 1);
                            notification.setStatus(NotificationStatus.PROCESSING);
                            notification.setUpdatedAt(LocalDateTime.now());
                            notificationRepository.save(notification);
                            
                            // Process the notification
                            processingService.processNotification(notification);
                            
                            logger.debug("Retried notification: {} (attempt {})", 
                                       notification.getId(), notification.getRetryCount());
                            
                        } else {
                            // Max retries exceeded, mark as permanently failed
                            notification.setStatus(NotificationStatus.EXPIRED);
                            notification.setUpdatedAt(LocalDateTime.now());
                            notificationRepository.save(notification);
                            
                            logger.warn("Max retries exceeded for notification: {}", notification.getId());
                        }
                        
                    } catch (Exception e) {
                        logger.error("Failed to retry notification: {}", notification.getId(), e);
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Error in failed notification retry processing", e);
        }
    }
    
    /**
     * Cleanup old notifications every hour
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    @Transactional
    public void cleanupOldNotifications() {
        logger.debug("Cleaning up old notifications");
        
        try {
            // Delete notifications older than 30 days
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
            
            List<Notification> oldNotifications = notificationRepository.findByCreatedAtBeforeAndStatusIn(
                cutoffDate, 
                List.of(NotificationStatus.DELIVERED, NotificationStatus.CANCELLED, NotificationStatus.EXPIRED)
            );
            
            if (!oldNotifications.isEmpty()) {
                notificationRepository.deleteAll(oldNotifications);
                logger.info("Cleaned up {} old notifications", oldNotifications.size());
            }
            
        } catch (Exception e) {
            logger.error("Error in old notification cleanup", e);
        }
    }
    
    /**
     * Generate notification statistics every 6 hours
     */
    @Scheduled(fixedRate = 21600000) // Every 6 hours
    public void generateNotificationStats() {
        logger.debug("Generating notification statistics");
        
        try {
            long totalNotifications = notificationRepository.count();
            long pendingNotifications = notificationRepository.countByStatus(NotificationStatus.PENDING);
            long processingNotifications = notificationRepository.countByStatus(NotificationStatus.PROCESSING);
            long sentNotifications = notificationRepository.countByStatus(NotificationStatus.SENT);
            long deliveredNotifications = notificationRepository.countByStatus(NotificationStatus.DELIVERED);
            long failedNotifications = notificationRepository.countByStatus(NotificationStatus.FAILED);
            long cancelledNotifications = notificationRepository.countByStatus(NotificationStatus.CANCELLED);
            long expiredNotifications = notificationRepository.countByStatus(NotificationStatus.EXPIRED);
            
            logger.info("Notification Statistics - Total: {}, Pending: {}, Processing: {}, Sent: {}, " +
                       "Delivered: {}, Failed: {}, Cancelled: {}, Expired: {}", 
                       totalNotifications, pendingNotifications, processingNotifications, sentNotifications,
                       deliveredNotifications, failedNotifications, cancelledNotifications, expiredNotifications);
            
        } catch (Exception e) {
            logger.error("Error in notification statistics generation", e);
        }
    }
    
    /**
     * Health check for scheduler service
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void healthCheck() {
        logger.debug("Notification scheduler health check - Service is running");
    }
}
