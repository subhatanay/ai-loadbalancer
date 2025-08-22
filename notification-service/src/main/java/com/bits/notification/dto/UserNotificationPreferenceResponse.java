package com.bits.notification.dto;

import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationPreferenceResponse {
    
    private Long id;
    private String userId;
    private NotificationType notificationType;
    private NotificationChannel channel;
    private Boolean enabled;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
    private String timezone;
    private Integer frequencyLimitPerDay;
    private Integer frequencyLimitPerHour;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Manual getters and setters for compatibility
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public NotificationType getNotificationType() { return notificationType; }
    public void setNotificationType(NotificationType notificationType) { this.notificationType = notificationType; }
    
    public NotificationChannel getChannel() { return channel; }
    public void setChannel(NotificationChannel channel) { this.channel = channel; }
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public LocalTime getQuietHoursStart() { return quietHoursStart; }
    public void setQuietHoursStart(LocalTime quietHoursStart) { this.quietHoursStart = quietHoursStart; }
    
    public LocalTime getQuietHoursEnd() { return quietHoursEnd; }
    public void setQuietHoursEnd(LocalTime quietHoursEnd) { this.quietHoursEnd = quietHoursEnd; }
    
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    
    public Integer getFrequencyLimitPerDay() { return frequencyLimitPerDay; }
    public void setFrequencyLimitPerDay(Integer frequencyLimitPerDay) { this.frequencyLimitPerDay = frequencyLimitPerDay; }
    
    public Integer getFrequencyLimitPerHour() { return frequencyLimitPerHour; }
    public void setFrequencyLimitPerHour(Integer frequencyLimitPerHour) { this.frequencyLimitPerHour = frequencyLimitPerHour; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    // Alias methods for service compatibility
    public NotificationType getType() { return notificationType; }
    public void setType(NotificationType type) { this.notificationType = type; }
}
