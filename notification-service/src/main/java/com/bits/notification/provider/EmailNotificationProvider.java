package com.bits.notification.provider;

import com.bits.notification.model.Notification;
import com.bits.notification.model.NotificationDelivery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationProvider implements NotificationProvider {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationProvider.class);
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Value("${notification.email.from:noreply@example.com}")
    private String fromEmail;
    
    @Value("${notification.email.enabled:true}")
    private boolean enabled;
    
    @Override
    public boolean sendNotification(Notification notification, NotificationDelivery delivery) {
        if (!enabled) {
            logger.warn("Email notifications are disabled");
            return false;
        }
        
        logger.info("Sending email notification: {} to: {}", notification.getId(), delivery.getRecipient());
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(delivery.getRecipient());
            message.setSubject(notification.getTitle());
            message.setText(notification.getMessage());
            
            mailSender.send(message);
            
            // Update delivery with provider message ID (would be actual message ID in real implementation)
            delivery.setProviderMessageId("email_" + System.currentTimeMillis());
            
            logger.info("Successfully sent email notification: {}", notification.getId());
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to send email notification: {}", notification.getId(), e);
            return false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "EmailProvider";
    }
    
    @Override
    public boolean isAvailable() {
        return enabled && mailSender != null;
    }
}
