package com.bits.notification.service;

import com.bits.notification.dto.CreateNotificationRequest;
import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationStatus;
import com.bits.notification.exception.NotificationValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class NotificationValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationValidationService.class);
    
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_CONTENT_LENGTH = 4000;
    private static final int MAX_RETRY_COUNT = 5;
    
    public void validateCreateRequest(CreateNotificationRequest request) {
        logger.debug("Validating create notification request for user: {}", request.getUserId());
        
        if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
            throw new NotificationValidationException("User ID is required");
        }
        
        if (request.getType() == null) {
            throw new NotificationValidationException("Notification type is required");
        }
        
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new NotificationValidationException("Notification title is required");
        }
        
        if (request.getTitle().length() > MAX_TITLE_LENGTH) {
            throw new NotificationValidationException("Title length cannot exceed " + MAX_TITLE_LENGTH + " characters");
        }
        
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new NotificationValidationException("Notification content is required");
        }
        
        if (request.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new NotificationValidationException("Content length cannot exceed " + MAX_CONTENT_LENGTH + " characters");
        }
        
        if (request.getPriority() == null) {
            throw new NotificationValidationException("Notification priority is required");
        }
        
        if (request.getChannels() == null || request.getChannels().isEmpty()) {
            throw new NotificationValidationException("At least one notification channel is required");
        }
        
        // Validate channels
        for (NotificationChannel channel : request.getChannels()) {
            if (channel == null) {
                throw new NotificationValidationException("Invalid notification channel");
            }
        }
        
        // Validate scheduled time
        if (request.getScheduledAt() != null && request.getScheduledAt().isBefore(LocalDateTime.now())) {
            throw new NotificationValidationException("Scheduled time cannot be in the past");
        }
        
        // Validate expiry time
        if (request.getExpiresAt() != null) {
            LocalDateTime compareTime = request.getScheduledAt() != null ? request.getScheduledAt() : LocalDateTime.now();
            if (request.getExpiresAt().isBefore(compareTime)) {
                throw new NotificationValidationException("Expiry time cannot be before scheduled time or current time");
            }
        }
        
        // Validate max retries
        if (request.getMaxRetries() != null && (request.getMaxRetries() < 0 || request.getMaxRetries() > MAX_RETRY_COUNT)) {
            throw new NotificationValidationException("Max retries must be between 0 and " + MAX_RETRY_COUNT);
        }
        
        logger.debug("Validation passed for create notification request");
    }
    
    public void validateStatusTransition(NotificationStatus currentStatus, NotificationStatus newStatus) {
        logger.debug("Validating status transition from {} to {}", currentStatus, newStatus);
        
        if (currentStatus == null || newStatus == null) {
            throw new NotificationValidationException("Current and new status are required");
        }
        
        if (currentStatus == newStatus) {
            throw new NotificationValidationException("New status must be different from current status");
        }
        
        // Define valid transitions
        List<NotificationStatus> validTransitions = getValidTransitions(currentStatus);
        
        if (!validTransitions.contains(newStatus)) {
            throw new NotificationValidationException(
                String.format("Invalid status transition from %s to %s", currentStatus, newStatus)
            );
        }
        
        logger.debug("Status transition validation passed");
    }
    
    private List<NotificationStatus> getValidTransitions(NotificationStatus currentStatus) {
        switch (currentStatus) {
            case PENDING:
                return Arrays.asList(
                    NotificationStatus.PROCESSING,
                    NotificationStatus.CANCELLED,
                    NotificationStatus.EXPIRED
                );
            case PROCESSING:
                return Arrays.asList(
                    NotificationStatus.SENT,
                    NotificationStatus.FAILED,
                    NotificationStatus.CANCELLED
                );
            case SENT:
                return Arrays.asList(
                    NotificationStatus.DELIVERED,
                    NotificationStatus.FAILED
                );
            case FAILED:
                return Arrays.asList(
                    NotificationStatus.PROCESSING,
                    NotificationStatus.CANCELLED,
                    NotificationStatus.EXPIRED
                );
            case DELIVERED:
            case CANCELLED:
            case EXPIRED:
                // Terminal states - no transitions allowed
                return Arrays.asList();
            default:
                return Arrays.asList();
        }
    }
    
    public void validateChannelSupport(NotificationChannel channel) {
        logger.debug("Validating channel support for: {}", channel);
        
        if (channel == null) {
            throw new NotificationValidationException("Notification channel is required");
        }
        
        // Add specific channel validation logic here
        switch (channel) {
            case EMAIL:
                // Email channel is always supported
                break;
            case SMS:
                // SMS channel validation
                break;
            case PUSH:
                // Push notification validation
                break;
            case IN_APP:
                // In-app notification validation
                break;
            case WEBHOOK:
                // Webhook validation
                break;
            default:
                throw new NotificationValidationException("Unsupported notification channel: " + channel);
        }
        
        logger.debug("Channel validation passed for: {}", channel);
    }
    
    public void validateRetryEligibility(NotificationStatus status, int currentRetryCount, int maxRetries) {
        logger.debug("Validating retry eligibility for status: {}, retryCount: {}, maxRetries: {}", 
                    status, currentRetryCount, maxRetries);
        
        if (status != NotificationStatus.FAILED) {
            throw new NotificationValidationException("Only failed notifications can be retried");
        }
        
        if (currentRetryCount >= maxRetries) {
            throw new NotificationValidationException("Maximum retry count exceeded");
        }
        
        logger.debug("Retry eligibility validation passed");
    }
    
    public void validateUserPreferences(String userId, NotificationChannel channel) {
        logger.debug("Validating user preferences for user: {}, channel: {}", userId, channel);
        
        if (userId == null || userId.trim().isEmpty()) {
            throw new NotificationValidationException("User ID is required for preference validation");
        }
        
        if (channel == null) {
            throw new NotificationValidationException("Channel is required for preference validation");
        }
        
        // This would typically check against user preferences in the database
        // For now, we'll just validate the inputs
        
        logger.debug("User preference validation passed");
    }
}
