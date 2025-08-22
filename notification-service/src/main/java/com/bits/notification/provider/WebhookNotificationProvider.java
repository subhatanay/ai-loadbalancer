package com.bits.notification.provider;

import com.bits.notification.model.Notification;
import com.bits.notification.model.NotificationDelivery;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class WebhookNotificationProvider implements NotificationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookNotificationProvider.class);
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${notification.webhook.enabled:true}")
    private boolean enabled;
    
    @Value("${notification.webhook.timeout-ms:5000}")
    private int timeoutMs;
    
    @Value("${notification.webhook.retry-attempts:3}")
    private int retryAttempts;
    
    @Override
    public boolean sendNotification(Notification notification, NotificationDelivery delivery) {
        if (!enabled) {
            logger.warn("Webhook notifications are disabled");
            return false;
        }
        
        logger.info("Sending webhook notification: {} to: {}", notification.getId(), delivery.getRecipient());
        
        String webhookUrl = delivery.getRecipient();
        
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                // Create webhook payload
                Map<String, Object> payload = createWebhookPayload(notification);
                
                // Set headers
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Notification-ID", notification.getId().toString());
                headers.set("X-Notification-Type", notification.getType().toString());
                headers.set("X-Notification-Priority", notification.getPriority().toString());
                headers.set("User-Agent", "NotificationService/1.0");
                
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
                
                // Send webhook
                ResponseEntity<String> response = restTemplate.exchange(
                    webhookUrl,
                    HttpMethod.POST,
                    request,
                    String.class
                );
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    // Update delivery with provider message ID
                    delivery.setProviderMessageId("webhook_" + System.currentTimeMillis());
                    
                    logger.info("Successfully sent webhook notification: {} (attempt {})", notification.getId(), attempt);
                    return true;
                } else {
                    logger.warn("Webhook returned non-success status: {} for notification: {} (attempt {})", 
                               response.getStatusCode(), notification.getId(), attempt);
                }
                
            } catch (Exception e) {
                logger.error("Failed to send webhook notification: {} (attempt {}/{})", 
                           notification.getId(), attempt, retryAttempts, e);
                
                if (attempt == retryAttempts) {
                    return false;
                }
                
                // Wait before retry
                try {
                    Thread.sleep(1000 * attempt); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        return false;
    }
    
    @Override
    public String getProviderName() {
        return "WebhookNotificationProvider";
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && restTemplate != null;
    }
    
    private Map<String, Object> createWebhookPayload(Notification notification) {
        Map<String, Object> payload = new HashMap<>();
        
        // Notification details
        payload.put("id", notification.getId());
        payload.put("userId", notification.getUserId());
        payload.put("type", notification.getType().toString());
        payload.put("title", notification.getTitle());
        payload.put("content", notification.getMessage());
        payload.put("priority", notification.getPriority().toString());
        payload.put("channels", notification.getChannels().toString());
        payload.put("status", notification.getStatus().toString());
        payload.put("createdAt", notification.getCreatedAt().toString());
        payload.put("scheduledAt", notification.getScheduledAt() != null ? notification.getScheduledAt().toString() : null);
        payload.put("expiresAt", notification.getExpiresAt() != null ? notification.getExpiresAt().toString() : null);
        
        // Metadata
        if (notification.getMetadata() != null && !notification.getMetadata().isEmpty()) {
            payload.put("metadata", notification.getMetadata());
        }
        
        // Webhook metadata
        Map<String, Object> webhookMeta = new HashMap<>();
        webhookMeta.put("timestamp", LocalDateTime.now().toString());
        webhookMeta.put("provider", getProviderName());
        webhookMeta.put("version", "1.0");
        payload.put("webhook", webhookMeta);
        
        return payload;
    }
}
