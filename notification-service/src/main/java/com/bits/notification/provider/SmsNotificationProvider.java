package com.bits.notification.provider;

import com.bits.notification.model.Notification;
import com.bits.notification.model.NotificationDelivery;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class SmsNotificationProvider implements NotificationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(SmsNotificationProvider.class);
    
    @Value("${notification.sms.twilio.account-sid:}")
    private String accountSid;
    
    @Value("${notification.sms.twilio.auth-token:}")
    private String authToken;
    
    @Value("${notification.sms.twilio.from-number:}")
    private String fromNumber;
    
    @Value("${notification.sms.enabled:true}")
    private boolean enabled;
    
    @PostConstruct
    public void init() {
        if (enabled && accountSid != null && !accountSid.isEmpty() && authToken != null && !authToken.isEmpty()) {
            try {
                Twilio.init(accountSid, authToken);
                logger.info("Twilio SMS provider initialized successfully");
            } catch (Exception e) {
                logger.error("Failed to initialize Twilio SMS provider", e);
                enabled = false;
            }
        } else {
            logger.warn("SMS provider not configured or disabled");
            enabled = false;
        }
    }
    
    @Override
    public boolean sendNotification(Notification notification, NotificationDelivery delivery) {
        if (!enabled) {
            logger.warn("SMS notifications are disabled or not configured");
            return false;
        }
        
        logger.info("Sending SMS notification: {} to: {}", notification.getId(), delivery.getRecipient());
        
        try {
            // Create SMS content (limit to 160 characters for standard SMS)
            String smsContent = notification.getTitle() + ": " + notification.getMessage();
            if (smsContent.length() > 160) {
                smsContent = smsContent.substring(0, 157) + "...";
            }
            
            Message message = Message.creator(
                new PhoneNumber(delivery.getRecipient()),
                new PhoneNumber(fromNumber),
                smsContent
            ).create();
            
            // Update delivery with provider message ID
            delivery.setProviderMessageId(message.getSid());
            
            logger.info("Successfully sent SMS notification: {} with SID: {}", notification.getId(), message.getSid());
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to send SMS notification: {}", notification.getId(), e);
            return false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "TwilioSmsProvider";
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && accountSid != null && !accountSid.isEmpty() && 
               authToken != null && !authToken.isEmpty() && 
               fromNumber != null && !fromNumber.isEmpty();
    }
}
