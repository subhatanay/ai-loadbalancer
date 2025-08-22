package com.bits.notification.provider;

import com.bits.notification.model.Notification;
import com.bits.notification.model.NotificationDelivery;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PushNotificationProvider implements NotificationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(PushNotificationProvider.class);
    
    @Value("${notification.push.enabled:true}")
    private boolean enabled;
    
    @Override
    public boolean sendNotification(Notification notification, NotificationDelivery delivery) {
        if (!enabled) {
            logger.warn("Push notifications are disabled");
            return false;
        }
        
        logger.info("Sending push notification: {} to: {}", notification.getId(), delivery.getRecipient());
        
        try {
            // Build the push notification message
            Message.Builder messageBuilder = Message.builder()
                .setToken(delivery.getRecipient()) // FCM token
                .setNotification(
                    com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.getTitle())
                        .setBody(notification.getMessage())
                        .build()
                );
            
            // Add custom data if available
            if (notification.getMetadata() != null && !notification.getMetadata().isEmpty()) {
                Map<String, String> data = new HashMap<>();
                notification.getMetadata().forEach((key, value) -> 
                    data.put(key, value != null ? value.toString() : ""));
                messageBuilder.putAllData(data);
            }
            
            // Add notification metadata
            messageBuilder.putData("notificationId", notification.getId().toString());
            messageBuilder.putData("type", notification.getType().toString());
            messageBuilder.putData("priority", notification.getPriority().toString());
            
            Message message = messageBuilder.build();
            
            // Send the message
            String response = FirebaseMessaging.getInstance().send(message);
            
            // Update delivery with provider message ID
            delivery.setProviderMessageId(response);
            
            logger.info("Successfully sent push notification: {} with response: {}", notification.getId(), response);
            return true;
            
        } catch (FirebaseMessagingException e) {
            logger.error("Firebase messaging error for notification: {} - Error code: {}, Message: {}", 
                        notification.getId(), e.getErrorCode(), e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Failed to send push notification: {}", notification.getId(), e);
            return false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "FirebasePushProvider";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            return enabled && FirebaseMessaging.getInstance() != null;
        } catch (Exception e) {
            logger.warn("Firebase messaging not available", e);
            return false;
        }
    }
}
