package com.bits.notification.service;

import com.bits.notification.dto.UpdateUserPreferenceRequest;
import com.bits.notification.dto.UserNotificationPreferenceResponse;
import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationType;
import com.bits.notification.exception.PreferenceNotFoundException;
import com.bits.notification.model.UserNotificationPreference;
import com.bits.notification.repository.UserNotificationPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserPreferenceService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserPreferenceService.class);
    
    @Autowired
    private UserNotificationPreferenceRepository preferenceRepository;
    
    @Cacheable(value = "userPreferences", key = "#userId + '_' + #type + '_' + #channel")
    @Transactional(readOnly = true)
    public UserNotificationPreferenceResponse getUserPreference(String userId, NotificationType type, NotificationChannel channel) {
        logger.debug("Fetching user preference for user: {}, type: {}, channel: {}", userId, type, channel);
        
        UserNotificationPreference preference = preferenceRepository.findByUserIdAndTypeAndChannel(userId, type, channel)
            .orElseThrow(() -> new PreferenceNotFoundException(
                "Preference not found for user: " + userId + ", type: " + type + ", channel: " + channel));
        
        return mapToResponse(preference);
    }
    
    @Transactional(readOnly = true)
    public List<UserNotificationPreferenceResponse> getUserPreferences(String userId) {
        logger.debug("Fetching all preferences for user: {}", userId);
        
        List<UserNotificationPreference> preferences = preferenceRepository.findByUserId(userId);
        return preferences.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<UserNotificationPreferenceResponse> getUserPreferencesByType(String userId, NotificationType type) {
        logger.debug("Fetching preferences for user: {} and type: {}", userId, type);
        
        List<UserNotificationPreference> preferences = preferenceRepository.findByUserIdAndType(userId, type);
        return preferences.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<UserNotificationPreferenceResponse> getUserPreferencesByChannel(String userId, NotificationChannel channel) {
        logger.debug("Fetching preferences for user: {} and channel: {}", userId, channel);
        
        List<UserNotificationPreference> preferences = preferenceRepository.findByUserIdAndChannel(userId, channel);
        return preferences.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    @CacheEvict(value = "userPreferences", key = "#userId + '_' + #type + '_' + #channel")
    @Transactional
    public UserNotificationPreferenceResponse updateUserPreference(String userId, NotificationType type, 
                                                                   NotificationChannel channel, UpdateUserPreferenceRequest request) {
        logger.info("Updating user preference for user: {}, type: {}, channel: {}", userId, type, channel);
        
        Optional<UserNotificationPreference> existingPreference = 
            preferenceRepository.findByUserIdAndTypeAndChannel(userId, type, channel);
        
        UserNotificationPreference preference;
        
        if (existingPreference.isPresent()) {
            preference = existingPreference.get();
            preference.setUpdatedAt(LocalDateTime.now());
        } else {
            preference = new UserNotificationPreference();
            preference.setUserId(userId);
            preference.setType(type);
            preference.setChannel(channel);
            preference.setCreatedAt(LocalDateTime.now());
            preference.setUpdatedAt(LocalDateTime.now());
        }
        
        // Update preference fields
        if (request.getEnabled() != null) {
            preference.setEnabled(request.getEnabled());
        }
        
        if (request.getQuietHoursStart() != null) {
            preference.setQuietHoursStart(request.getQuietHoursStart());
        }
        
        if (request.getQuietHoursEnd() != null) {
            preference.setQuietHoursEnd(request.getQuietHoursEnd());
        }
        
        if (request.getTimezone() != null) {
            preference.setTimezone(request.getTimezone());
        }
        
        if (request.getFrequencyLimitPerHour() != null) {
            preference.setFrequencyLimitPerHour(request.getFrequencyLimitPerHour());
        }
        
        if (request.getFrequencyLimitPerDay() != null) {
            preference.setFrequencyLimitPerDay(request.getFrequencyLimitPerDay());
        }
        
        preference = preferenceRepository.save(preference);
        
        logger.info("Updated user preference with ID: {}", preference.getId());
        return mapToResponse(preference);
    }
    
    @CacheEvict(value = "userPreferences", allEntries = true)
    @Transactional
    public void createDefaultPreferences(String userId) {
        logger.info("Creating default preferences for user: {}", userId);
        
        // Create default preferences for all notification types and channels
        for (NotificationType type : NotificationType.values()) {
            for (NotificationChannel channel : NotificationChannel.values()) {
                
                // Check if preference already exists
                Optional<UserNotificationPreference> existing = 
                    preferenceRepository.findByUserIdAndTypeAndChannel(userId, type, channel);
                
                if (existing.isEmpty()) {
                    UserNotificationPreference preference = new UserNotificationPreference();
                    preference.setUserId(userId);
                    preference.setType(type);
                    preference.setChannel(channel);
                    preference.setEnabled(getDefaultEnabledStatus(type, channel));
                    preference.setQuietHoursStart(LocalTime.of(22, 0)); // 10 PM
                    preference.setQuietHoursEnd(LocalTime.of(8, 0));    // 8 AM
                    preference.setTimezone("UTC");
                    preference.setFrequencyLimitPerHour(getDefaultHourlyLimit(type));
                    preference.setFrequencyLimitPerDay(getDefaultDailyLimit(type));
                    preference.setCreatedAt(LocalDateTime.now());
                    preference.setUpdatedAt(LocalDateTime.now());
                    
                    preferenceRepository.save(preference);
                }
            }
        }
        
        logger.info("Created default preferences for user: {}", userId);
    }
    
    @Transactional(readOnly = true)
    public boolean isNotificationAllowed(String userId, NotificationType type, NotificationChannel channel) {
        logger.debug("Checking if notification is allowed for user: {}, type: {}, channel: {}", userId, type, channel);
        
        Optional<UserNotificationPreference> preference = 
            preferenceRepository.findByUserIdAndTypeAndChannel(userId, type, channel);
        
        if (preference.isEmpty()) {
            // If no preference exists, create default and allow
            createDefaultPreferences(userId);
            return true;
        }
        
        UserNotificationPreference pref = preference.get();
        
        // Check if notifications are enabled
        if (!pref.getEnabled()) {
            logger.debug("Notifications disabled for user: {}, type: {}, channel: {}", userId, type, channel);
            return false;
        }
        
        // Check quiet hours
        if (pref.isInQuietHours(LocalTime.now())) {
            logger.debug("Currently in quiet hours for user: {}", userId);
            return false;
        }
        
        // Additional frequency limit checks would go here
        // For now, we'll just return true if enabled and not in quiet hours
        
        return true;
    }
    
    @CacheEvict(value = "userPreferences", key = "#userId + '_' + #type + '_' + #channel")
    @Transactional
    public void deleteUserPreference(String userId, NotificationType type, NotificationChannel channel) {
        logger.info("Deleting user preference for user: {}, type: {}, channel: {}", userId, type, channel);
        
        UserNotificationPreference preference = preferenceRepository.findByUserIdAndTypeAndChannel(userId, type, channel)
            .orElseThrow(() -> new PreferenceNotFoundException(
                "Preference not found for user: " + userId + ", type: " + type + ", channel: " + channel));
        
        preferenceRepository.delete(preference);
        
        logger.info("Deleted user preference with ID: {}", preference.getId());
    }
    
    @CacheEvict(value = "userPreferences", allEntries = true)
    @Transactional
    public void deleteAllUserPreferences(String userId) {
        logger.info("Deleting all preferences for user: {}", userId);
        
        List<UserNotificationPreference> preferences = preferenceRepository.findByUserId(userId);
        preferenceRepository.deleteAll(preferences);
        
        logger.info("Deleted {} preferences for user: {}", preferences.size(), userId);
    }
    
    private boolean getDefaultEnabledStatus(NotificationType type, NotificationChannel channel) {
        // Define default enabled status based on type and channel
        if (type == NotificationType.ORDER_CONFIRMATION || type == NotificationType.PAYMENT_SUCCESS || 
            type == NotificationType.PAYMENT_FAILED || type == NotificationType.ORDER_SHIPPED || 
            type == NotificationType.ORDER_DELIVERED) {
            return true; // Always enable critical notifications
        } else if (type == NotificationType.PROMOTIONAL || type == NotificationType.CART_ABANDONMENT) {
            return channel != NotificationChannel.SMS; // Enable for all except SMS
        } else if (type == NotificationType.SYSTEM_ALERT || type == NotificationType.PASSWORD_RESET) {
            return true; // Always enable security notifications
        } else {
            return true;
        }
    }
    
    private Integer getDefaultHourlyLimit(NotificationType type) {
        if (type == NotificationType.PROMOTIONAL || type == NotificationType.CART_ABANDONMENT) {
            return 2;
        } else if (type == NotificationType.ORDER_CONFIRMATION || type == NotificationType.PAYMENT_SUCCESS || 
                   type == NotificationType.PAYMENT_FAILED) {
            return 10;
        } else if (type == NotificationType.SYSTEM_ALERT) {
            return 5;
        } else {
            return 5;
        }
    }
    
    private Integer getDefaultDailyLimit(NotificationType type) {
        if (type == NotificationType.PROMOTIONAL || type == NotificationType.CART_ABANDONMENT) {
            return 5;
        } else if (type == NotificationType.ORDER_CONFIRMATION || type == NotificationType.PAYMENT_SUCCESS || 
                   type == NotificationType.PAYMENT_FAILED) {
            return 50;
        } else if (type == NotificationType.SYSTEM_ALERT) {
            return 20;
        } else {
            return 20;
        }
    }
    
    private UserNotificationPreferenceResponse mapToResponse(UserNotificationPreference preference) {
        UserNotificationPreferenceResponse response = new UserNotificationPreferenceResponse();
        response.setId(preference.getId());
        response.setUserId(preference.getUserId());
        response.setType(preference.getType());
        response.setChannel(preference.getChannel());
        response.setEnabled(preference.getEnabled());
        response.setQuietHoursStart(preference.getQuietHoursStart());
        response.setQuietHoursEnd(preference.getQuietHoursEnd());
        response.setTimezone(preference.getTimezone());
        response.setFrequencyLimitPerHour(preference.getFrequencyLimitPerHour());
        response.setFrequencyLimitPerDay(preference.getFrequencyLimitPerDay());
        response.setCreatedAt(preference.getCreatedAt());
        response.setUpdatedAt(preference.getUpdatedAt());
        
        return response;
    }
}
