package com.bits.notification.dto;

import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationPriority;
import com.bits.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNotificationRequest {
    
    @NotBlank(message = "User ID cannot be blank")
    private String userId;
    
    private String userEmail;
    
    private String userPhone;
    
    @NotNull(message = "Notification type cannot be null")
    private NotificationType type;
    
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;
    
    @NotBlank(message = "Title cannot be blank")
    @Size(max = 255, message = "Title cannot exceed 255 characters")
    private String title;
    
    @NotBlank(message = "Message cannot be blank")
    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    private String message;
    
    @NotEmpty(message = "At least one channel must be specified")
    private List<NotificationChannel> channels;
    
    private Map<String, String> metadata;
    
    private String templateId;
    
    private LocalDateTime scheduledAt;
    
    private LocalDateTime expiresAt;
    
    @Min(value = 0, message = "Max retries cannot be negative")
    @Max(value = 10, message = "Max retries cannot exceed 10")
    private Integer maxRetries;
    
    // Manual getters and setters for compatibility
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
    
    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    
    // Alias method for service compatibility
    public String getContent() { return message; }
}
