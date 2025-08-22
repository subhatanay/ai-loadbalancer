package com.bits.notification.provider;

import com.bits.notification.model.Notification;
import com.bits.notification.model.NotificationDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class InAppNotificationProvider implements NotificationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(InAppNotificationProvider.class);
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Value("${notification.inapp.enabled:true}")
    private boolean enabled;
    
    @Value("${notification.inapp.ttl-hours:24}")
    private int ttlHours;
    
    private static final String IN_APP_NOTIFICATION_KEY_PREFIX = "inapp:notification:";
    private static final String USER_NOTIFICATION_LIST_KEY_PREFIX = "inapp:user:";
    
    @Override
    public boolean sendNotification(Notification notification, NotificationDelivery delivery) {
        if (!enabled) {
            logger.warn("In-app notifications are disabled");
            return false;
        }
        
        logger.info("Storing in-app notification: {} for user: {}", notification.getId(), delivery.getRecipient());
        
        try {
            String userId = delivery.getRecipient();
            String notificationKey = IN_APP_NOTIFICATION_KEY_PREFIX + notification.getId();
            String userListKey = USER_NOTIFICATION_LIST_KEY_PREFIX + userId;
            
            // Create in-app notification data
            Map<String, Object> inAppNotification = new HashMap<>();
            inAppNotification.put("id", notification.getId());
            inAppNotification.put("userId", notification.getUserId());
            inAppNotification.put("type", notification.getType().toString());
            inAppNotification.put("title", notification.getTitle());
            inAppNotification.put("content", notification.getMessage());
            inAppNotification.put("priority", notification.getPriority().toString());
            inAppNotification.put("metadata", notification.getMetadata());
            inAppNotification.put("createdAt", notification.getCreatedAt().toString());
            inAppNotification.put("read", false);
            inAppNotification.put("deliveredAt", LocalDateTime.now().toString());
            
            // Store the notification
            redisTemplate.opsForValue().set(notificationKey, inAppNotification, ttlHours, TimeUnit.HOURS);
            
            // Add to user's notification list
            redisTemplate.opsForList().leftPush(userListKey, notification.getId().toString());
            redisTemplate.expire(userListKey, ttlHours, TimeUnit.HOURS);
            
            // Update delivery with provider message ID
            delivery.setProviderMessageId("inapp_" + notification.getId());
            
            logger.info("Successfully stored in-app notification: {} for user: {}", notification.getId(), userId);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to store in-app notification: {}", notification.getId(), e);
            return false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "InAppNotificationProvider";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            return enabled && redisTemplate != null && redisTemplate.getConnectionFactory() != null;
        } catch (Exception e) {
            logger.warn("Redis connection not available for in-app notifications", e);
            return false;
        }
    }
    
    /**
     * Mark an in-app notification as read
     */
    public boolean markAsRead(Long notificationId, String userId) {
        try {
            String notificationKey = IN_APP_NOTIFICATION_KEY_PREFIX + notificationId;
            
            @SuppressWarnings("unchecked")
            Map<String, Object> notification = (Map<String, Object>) redisTemplate.opsForValue().get(notificationKey);
            
            if (notification != null && userId.equals(notification.get("userId"))) {
                notification.put("read", true);
                notification.put("readAt", LocalDateTime.now().toString());
                
                redisTemplate.opsForValue().set(notificationKey, notification, ttlHours, TimeUnit.HOURS);
                
                logger.info("Marked in-app notification {} as read for user: {}", notificationId, userId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            logger.error("Failed to mark in-app notification {} as read", notificationId, e);
            return false;
        }
    }
    
    /**
     * Get unread count for a user
     */
    public long getUnreadCount(String userId) {
        try {
            String userListKey = USER_NOTIFICATION_LIST_KEY_PREFIX + userId;
            Long listSize = redisTemplate.opsForList().size(userListKey);
            
            if (listSize == null || listSize == 0) {
                return 0;
            }
            
            // Count unread notifications
            long unreadCount = 0;
            for (int i = 0; i < listSize; i++) {
                String notificationId = (String) redisTemplate.opsForList().index(userListKey, i);
                if (notificationId != null) {
                    String notificationKey = IN_APP_NOTIFICATION_KEY_PREFIX + notificationId;
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> notification = (Map<String, Object>) redisTemplate.opsForValue().get(notificationKey);
                    
                    if (notification != null && !Boolean.TRUE.equals(notification.get("read"))) {
                        unreadCount++;
                    }
                }
            }
            
            return unreadCount;
            
        } catch (Exception e) {
            logger.error("Failed to get unread count for user: {}", userId, e);
            return 0;
        }
    }
}
