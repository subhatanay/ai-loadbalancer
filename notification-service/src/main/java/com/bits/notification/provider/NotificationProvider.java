package com.bits.notification.provider;

import com.bits.notification.model.Notification;
import com.bits.notification.model.NotificationDelivery;

/**
 * Base interface for all notification providers
 */
public interface NotificationProvider {
    
    /**
     * Send notification through this provider
     * 
     * @param notification The notification to send
     * @param delivery The delivery record
     * @return true if sent successfully, false otherwise
     */
    boolean sendNotification(Notification notification, NotificationDelivery delivery);
    
    /**
     * Get the provider name
     * 
     * @return Provider name
     */
    String getProviderName();
    
    /**
     * Check if the provider is available/healthy
     * 
     * @return true if available, false otherwise
     */
    boolean isAvailable();
}
