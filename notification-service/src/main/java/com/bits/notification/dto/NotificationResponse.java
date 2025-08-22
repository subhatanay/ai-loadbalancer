package com.bits.notification.dto;

import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationPriority;
import com.bits.notification.enums.NotificationStatus;
import com.bits.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    
    private Long id;
    private String notificationId;
    private String userId;
    private String userEmail;
    private String userPhone;
    private NotificationType type;
    private NotificationPriority priority;
    private String title;
    private String message;
    private List<NotificationChannel> channels;
    private Map<String, String> metadata;
    private NotificationStatus status;
    private String templateId;
    private LocalDateTime scheduledAt;
    private LocalDateTime expiresAt;
    private Integer retryCount;
    private Integer maxRetries;
    private String errorMessage;
    private List<NotificationDeliveryResponse> deliveries;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Manual getters and setters for compatibility
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    
    public String getUserPhone() { return userPhone; }
    public void setUserPhone(String userPhone) { this.userPhone = userPhone; }
    
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    
    public NotificationPriority getPriority() { return priority; }
    public void setPriority(NotificationPriority priority) { this.priority = priority; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public List<NotificationChannel> getChannels() { return channels; }
    public void setChannels(List<NotificationChannel> channels) { this.channels = channels; }
    
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    public List<NotificationDeliveryResponse> getDeliveries() { return deliveries; }
    public void setDeliveries(List<NotificationDeliveryResponse> deliveries) { this.deliveries = deliveries; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    // Alias methods for service compatibility
    public void setContent(String content) { this.message = content; }
    public void setSentAt(LocalDateTime sentAt) { /* Implementation based on deliveries */ }
    public void setDeliveredAt(LocalDateTime deliveredAt) { /* Implementation based on deliveries */ }
    public void setVersion(Long version) { /* Version field not used in this DTO */ }
}
