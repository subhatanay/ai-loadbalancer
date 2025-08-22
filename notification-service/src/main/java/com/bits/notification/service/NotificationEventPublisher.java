package com.bits.notification.service;

import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationStatus;
import com.bits.notification.event.*;
import com.bits.notification.model.Notification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NotificationEventPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationEventPublisher.class);
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${kafka.topics.notification-events:notification-events}")
    private String notificationEventsTopic;
    
    public void publishNotificationCreated(Notification notification) {
        try {
            NotificationCreatedEvent event = NotificationCreatedEvent.builder()
                .notificationId(notification.getId().toString())
                .userId(notification.getUserId())
                .type(notification.getType().toString())
                .priority(notification.getPriority().toString())
                .channels(notification.getChannels().stream()
                    .map(NotificationChannel::toString)
                    .collect(java.util.stream.Collectors.toList()))
                .scheduledAt(notification.getScheduledAt())
                .timestamp(LocalDateTime.now())
                .eventType("NOTIFICATION_CREATED")
                .build();
            
            publishEvent(event, "notification.created");
            logger.info("Published notification created event for ID: {}", notification.getId());
            
        } catch (Exception e) {
            logger.error("Failed to publish notification created event for ID: {}", notification.getId(), e);
        }
    }
    
    public void publishNotificationProcessed(Notification notification) {
        try {
            NotificationProcessedEvent event = NotificationProcessedEvent.builder()
                .notificationId(notification.getId().toString())
                .userId(notification.getUserId())
                .status(notification.getStatus().toString())
                .channels(notification.getChannels().stream()
                    .map(NotificationChannel::toString)
                    .collect(java.util.stream.Collectors.toList()))
                .sentAt(notification.getSentAt())
                .deliveredAt(notification.getDeliveredAt())
                .timestamp(LocalDateTime.now())
                .eventType("NOTIFICATION_PROCESSED")
                .build();
            
            publishEvent(event, "notification.processed");
            logger.info("Published notification processed event for ID: {}", notification.getId());
            
        } catch (Exception e) {
            logger.error("Failed to publish notification processed event for ID: {}", notification.getId(), e);
        }
    }
    
    public void publishNotificationUpdated(Notification notification, NotificationStatus previousStatus) {
        try {
            NotificationUpdatedEvent event = NotificationUpdatedEvent.builder()
                .notificationId(notification.getId().toString())
                .userId(notification.getUserId())
                .previousStatus(previousStatus.toString())
                .newStatus(notification.getStatus().toString())
                .timestamp(LocalDateTime.now())
                .eventType("NOTIFICATION_UPDATED")
                .build();
            
            publishEvent(event, "notification.updated");
            logger.info("Published notification updated event for ID: {}", notification.getId());
            
        } catch (Exception e) {
            logger.error("Failed to publish notification updated event for ID: {}", notification.getId(), e);
        }
    }
    
    public void publishNotificationCancelled(Notification notification, String reason) {
        try {
            NotificationCancelledEvent event = NotificationCancelledEvent.builder()
                .notificationId(notification.getId().toString())
                .userId(notification.getUserId())
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .eventType("NOTIFICATION_CANCELLED")
                .build();
            
            publishEvent(event, "notification.cancelled");
            logger.info("Published notification cancelled event for ID: {}", notification.getId());
            
        } catch (Exception e) {
            logger.error("Failed to publish notification cancelled event for ID: {}", notification.getId(), e);
        }
    }
    
    public void publishNotificationExpired(Notification notification) {
        try {
            NotificationExpiredEvent event = NotificationExpiredEvent.builder()
                .notificationId(notification.getId().toString())
                .userId(notification.getUserId())
                .expiryTime(notification.getExpiresAt())
                .timestamp(LocalDateTime.now())
                .eventType("NOTIFICATION_EXPIRED")
                .build();
            
            publishEvent(event, "notification.expired");
            logger.info("Published notification expired event for ID: {}", notification.getId());
            
        } catch (Exception e) {
            logger.error("Failed to publish notification expired event for ID: {}", notification.getId(), e);
        }
    }
    
    private void publishEvent(Object event, String eventKey) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(notificationEventsTopic, eventKey, eventJson);
            logger.debug("Published event to topic {}: {}", notificationEventsTopic, eventKey);
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize event: {}", eventKey, e);
            throw new RuntimeException("Failed to publish event", e);
        }
    }
}
