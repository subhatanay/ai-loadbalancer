package com.bits.notification.service;

import com.bits.notification.dto.NotificationDeliveryResponse;
import com.bits.notification.enums.DeliveryStatus;
import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationStatus;
import com.bits.notification.model.Notification;
import com.bits.notification.model.NotificationDelivery;
import com.bits.notification.repository.NotificationDeliveryRepository;
import com.bits.notification.provider.EmailNotificationProvider;
import com.bits.notification.provider.InAppNotificationProvider;
import com.bits.notification.provider.PushNotificationProvider;
import com.bits.notification.provider.SmsNotificationProvider;
import com.bits.notification.provider.WebhookNotificationProvider;
import com.bits.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class NotificationProcessingService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationProcessingService.class);
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @Autowired
    private NotificationDeliveryService deliveryService;
    
    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    
    @Autowired
    private UserPreferenceService userPreferenceService;
    
    @Autowired
    private NotificationEventPublisher eventPublisher;
    
    @Autowired
    private EmailNotificationProvider emailProvider;
    
    @Autowired
    private SmsNotificationProvider smsProvider;
    
    @Autowired
    private PushNotificationProvider pushProvider;
    
    @Autowired
    private InAppNotificationProvider inAppProvider;
    
    @Autowired
    private WebhookNotificationProvider webhookProvider;
    
    public void processNotification(Notification notification) {
        logger.info("Processing notification: {}", notification.getId());
        
        try {
            // Update status to processing
            notification.setStatus(NotificationStatus.PROCESSING);
            notification.setUpdatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
            
            boolean hasSuccessfulDelivery = false;
            
            // Process each channel
            for (NotificationChannel channel : notification.getChannels()) {
                try {
                    // Check user preferences
                    if (!userPreferenceService.isNotificationAllowed(
                            notification.getUserId(), notification.getType(), channel)) {
                        logger.info("Notification blocked by user preferences - User: {}, Type: {}, Channel: {}", 
                                   notification.getUserId(), notification.getType(), channel);
                        continue;
                    }
                    
                    // Create delivery record
                    String recipient = getRecipientForChannel(notification.getUserId(), channel);
                    NotificationDelivery delivery = createDeliveryRecord(notification, channel, recipient);
                    
                    // Send notification through appropriate provider
                    boolean deliverySuccess = sendThroughChannel(notification, delivery, channel);
                    
                    if (deliverySuccess) {
                        hasSuccessfulDelivery = true;
                        updateDeliverySuccess(delivery);
                    } else {
                        updateDeliveryFailure(delivery, "Delivery failed through provider");
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to process notification {} through channel {}", 
                               notification.getId(), channel, e);
                }
            }
            
            // Update notification status based on delivery results
            updateNotificationStatus(notification, hasSuccessfulDelivery);
            
            // Publish processed event
            eventPublisher.publishNotificationProcessed(notification);
            
            logger.info("Completed processing notification: {}", notification.getId());
            
        } catch (Exception e) {
            logger.error("Failed to process notification: {}", notification.getId(), e);
            
            // Mark as failed
            notification.setStatus(NotificationStatus.FAILED);
            notification.setUpdatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }
    
    private NotificationDelivery createDeliveryRecord(Notification notification, 
                                                      NotificationChannel channel, String recipient) {
        String providerId = getProviderIdForChannel(channel);
        NotificationDeliveryResponse response = deliveryService.createDelivery(notification.getId(), channel, recipient, providerId);
        // Get the actual entity from repository using the response ID
        return deliveryRepository.findById(response.getId())
            .orElseThrow(() -> new RuntimeException("Failed to retrieve created delivery"));
    }
    
    private boolean sendThroughChannel(Notification notification, NotificationDelivery delivery, 
                                       NotificationChannel channel) {
        logger.debug("Sending notification {} through channel {}", notification.getId(), channel);
        
        try {
            switch (channel) {
                case EMAIL:
                    return emailProvider.sendNotification(notification, delivery);
                case SMS:
                    return smsProvider.sendNotification(notification, delivery);
                case PUSH:
                    return pushProvider.sendNotification(notification, delivery);
                case IN_APP:
                    return inAppProvider.sendNotification(notification, delivery);
                case WEBHOOK:
                    return webhookProvider.sendNotification(notification, delivery);
                default:
                    logger.warn("Unsupported notification channel: {}", channel);
                    return false;
            }
        } catch (Exception e) {
            logger.error("Error sending notification through channel {}", channel, e);
            return false;
        }
    }
    
    private void updateDeliverySuccess(NotificationDelivery delivery) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("success", true);
        metadata.put("timestamp", LocalDateTime.now());
        
        deliveryService.updateDeliveryStatus(
            delivery.getId(), 
            DeliveryStatus.SENT, 
            null, 
            metadata
        );
    }
    
    private void updateDeliveryFailure(NotificationDelivery delivery, String errorMessage) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("success", false);
        metadata.put("timestamp", LocalDateTime.now());
        metadata.put("error", errorMessage);
        
        deliveryService.updateDeliveryStatus(
            delivery.getId(), 
            DeliveryStatus.FAILED, 
            errorMessage, 
            metadata
        );
    }
    
    private void updateNotificationStatus(Notification notification, boolean hasSuccessfulDelivery) {
        if (hasSuccessfulDelivery) {
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        } else {
            notification.setStatus(NotificationStatus.FAILED);
        }
        
        notification.setUpdatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }
    
    private String getRecipientForChannel(String userId, NotificationChannel channel) {
        // This would typically fetch user contact information from a user service
        // For now, we'll return placeholder values
        switch (channel) {
            case EMAIL:
                return userId + "@example.com"; // Would fetch actual email
            case SMS:
                return "+1234567890"; // Would fetch actual phone number
            case PUSH:
                return "push_token_" + userId; // Would fetch actual push token
            case IN_APP:
                return userId; // User ID for in-app notifications
            case WEBHOOK:
                return "https://webhook.example.com/user/" + userId; // Would fetch actual webhook URL
            default:
                return userId;
        }
    }
    
    private String getProviderIdForChannel(NotificationChannel channel) {
        switch (channel) {
            case EMAIL:
                return "email_provider";
            case SMS:
                return "sms_provider";
            case PUSH:
                return "push_provider";
            case IN_APP:
                return "inapp_provider";
            case WEBHOOK:
                return "webhook_provider";
            default:
                return "unknown_provider";
        }
    }
    
    public void processDeliveryCallback(String providerMessageId, DeliveryStatus status, String errorMessage) {
        logger.info("Processing delivery callback - Provider Message ID: {}, Status: {}", providerMessageId, status);
        
        try {
            // Find delivery by provider message ID
            // This would require adding a method to the repository
            // For now, we'll log the callback
            logger.info("Delivery callback processed - Provider Message ID: {}, Status: {}", providerMessageId, status);
            
        } catch (Exception e) {
            logger.error("Failed to process delivery callback", e);
        }
    }
}
