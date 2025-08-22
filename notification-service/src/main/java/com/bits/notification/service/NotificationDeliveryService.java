package com.bits.notification.service;

import com.bits.notification.dto.NotificationDeliveryResponse;
import com.bits.notification.enums.DeliveryStatus;
import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.exception.NotificationNotFoundException;
import com.bits.notification.model.Notification;
import com.bits.notification.model.NotificationDelivery;
import com.bits.notification.repository.NotificationDeliveryRepository;
import com.bits.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationDeliveryService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationDeliveryService.class);
    
    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @Transactional
    public NotificationDeliveryResponse createDelivery(Long notificationId, NotificationChannel channel, 
                                                       String recipient, String providerId) {
        logger.info("Creating delivery for notification: {}, channel: {}, recipient: {}", 
                   notificationId, channel, recipient);
        
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new NotificationNotFoundException("Notification not found with ID: " + notificationId));
        
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setNotification(notification);
        delivery.setChannel(channel);
        delivery.setRecipient(recipient);
        delivery.setProviderId(providerId);
        delivery.setStatus(DeliveryStatus.PENDING);
        delivery.setRetryCount(0);
        delivery.setCreatedAt(LocalDateTime.now());
        delivery.setUpdatedAt(LocalDateTime.now());
        
        delivery = deliveryRepository.save(delivery);
        
        logger.info("Created delivery with ID: {}", delivery.getId());
        return mapToResponse(delivery);
    }
    
    @Transactional(readOnly = true)
    public NotificationDeliveryResponse getDelivery(Long id) {
        logger.debug("Fetching delivery with ID: {}", id);
        
        NotificationDelivery delivery = deliveryRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Delivery not found with ID: " + id));
        
        return mapToResponse(delivery);
    }
    
    @Transactional(readOnly = true)
    public List<NotificationDeliveryResponse> getDeliveriesByNotification(Long notificationId) {
        logger.debug("Fetching deliveries for notification: {}", notificationId);
        
        List<NotificationDelivery> deliveries = deliveryRepository.findByNotificationIdOrderByCreatedAtDesc(notificationId);
        return deliveries.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public Page<NotificationDeliveryResponse> getDeliveriesByChannel(NotificationChannel channel, Pageable pageable) {
        logger.debug("Fetching deliveries for channel: {}", channel);
        
        Page<NotificationDelivery> deliveries = deliveryRepository.findByChannelOrderByCreatedAtDesc(channel, pageable);
        return deliveries.map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    public Page<NotificationDeliveryResponse> getDeliveriesByStatus(DeliveryStatus status, Pageable pageable) {
        logger.debug("Fetching deliveries with status: {}", status);
        
        Page<NotificationDelivery> deliveries = deliveryRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return deliveries.map(this::mapToResponse);
    }
    
    @Transactional
    public NotificationDeliveryResponse updateDeliveryStatus(Long id, DeliveryStatus status, 
                                                             String errorMessage, Map<String, Object> metadata) {
        logger.info("Updating delivery status for ID: {} to status: {}", id, status);
        
        NotificationDelivery delivery = deliveryRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Delivery not found with ID: " + id));
        
        delivery.setStatus(status);
        delivery.setUpdatedAt(LocalDateTime.now());
        
        if (errorMessage != null) {
            delivery.setErrorMessage(errorMessage);
        }
        
        if (metadata != null) {
            // Convert Map<String, Object> to Map<String, String>
            Map<String, String> stringMetadata = metadata.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue() != null ? entry.getValue().toString() : null
                ));
            delivery.setMetadata(stringMetadata);
        }
        
        // Update timestamps based on status
        switch (status) {
            case PROCESSING:
                // No specific timestamp update for processing
                break;
            case SENT:
                delivery.setSentAt(LocalDateTime.now());
                break;
            case DELIVERED:
                if (delivery.getSentAt() == null) {
                    delivery.setSentAt(LocalDateTime.now());
                }
                delivery.setDeliveredAt(LocalDateTime.now());
                break;
            case FAILED:
            case REJECTED:
                delivery.setFailedAt(LocalDateTime.now());
                break;
            case PENDING:
            case BOUNCED:
            default:
                // No specific timestamp update
                break;
        }
        
        delivery = deliveryRepository.save(delivery);
        
        logger.info("Updated delivery status for ID: {} to status: {}", id, status);
        return mapToResponse(delivery);
    }
    
    @Transactional
    public NotificationDeliveryResponse incrementAttemptCount(Long id) {
        logger.debug("Incrementing attempt count for delivery: {}", id);
        
        NotificationDelivery delivery = deliveryRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Delivery not found with ID: " + id));
        
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setUpdatedAt(LocalDateTime.now());
        
        delivery = deliveryRepository.save(delivery);
        
        logger.debug("Incremented attempt count to {} for delivery: {}", delivery.getAttemptCount(), id);
        return mapToResponse(delivery);
    }
    
    @Transactional(readOnly = true)
    public List<NotificationDeliveryResponse> getFailedDeliveries() {
        logger.debug("Fetching failed deliveries for retry");
        
        List<NotificationDelivery> deliveries = deliveryRepository.findFailedDeliveriesForRetry();
        return deliveries.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public long getDeliveryCountByStatus(DeliveryStatus status) {
        logger.debug("Getting delivery count for status: {}", status);
        
        return deliveryRepository.countByStatus(status);
    }
    
    @Transactional(readOnly = true)
    public long getDeliveryCountByChannel(NotificationChannel channel) {
        logger.debug("Getting delivery count for channel: {}", channel);
        
        return deliveryRepository.countByChannel(channel);
    }
    
    @Transactional(readOnly = true)
    public List<NotificationDeliveryResponse> getRecentDeliveries(int limit) {
        logger.debug("Fetching {} recent deliveries", limit);
        
        List<NotificationDelivery> deliveries = deliveryRepository.findRecentDeliveries(limit);
        return deliveries.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public void deleteOldDeliveries(LocalDateTime cutoffDate) {
        logger.info("Deleting deliveries older than: {}", cutoffDate);
        
        List<NotificationDelivery> oldDeliveries = deliveryRepository.findByCreatedAtBefore(cutoffDate);
        deliveryRepository.deleteAll(oldDeliveries);
        
        logger.info("Deleted {} old deliveries", oldDeliveries.size());
    }
    
    @Transactional(readOnly = true)
    public boolean hasSuccessfulDelivery(Long notificationId, NotificationChannel channel) {
        logger.debug("Checking for successful delivery for notification: {}, channel: {}", notificationId, channel);
        
        return deliveryRepository.existsByNotificationIdAndChannelAndStatus(
            notificationId, channel, DeliveryStatus.DELIVERED);
    }
    
    private NotificationDeliveryResponse mapToResponse(NotificationDelivery delivery) {
        NotificationDeliveryResponse response = new NotificationDeliveryResponse();
        response.setId(delivery.getId());
        response.setNotificationId(delivery.getNotification().getId());
        response.setChannel(delivery.getChannel());
        response.setRecipient(delivery.getRecipient());
        response.setProviderId(delivery.getProviderId());
        response.setProviderMessageId(delivery.getProviderMessageId());
        response.setStatus(delivery.getStatus());
        response.setAttemptCount(delivery.getAttemptCount());
        response.setErrorMessage(delivery.getErrorMessage());
        response.setMetadata(delivery.getMetadata());
        response.setCreatedAt(delivery.getCreatedAt());
        response.setUpdatedAt(delivery.getUpdatedAt());
        response.setSentAt(delivery.getSentAt());
        response.setDeliveredAt(delivery.getDeliveredAt());
        response.setFailedAt(delivery.getFailedAt());
        
        return response;
    }
}
