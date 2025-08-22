package com.bits.notification.model;

import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "user_notification_preferences", 
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"user_id", "notification_type", "channel"})
       },
       indexes = {
           @Index(name = "idx_preference_user", columnList = "user_id"),
           @Index(name = "idx_preference_type", columnList = "notification_type")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNotificationPreference {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private NotificationChannel channel;
    
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;
    
    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;
    
    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;
    
    @Column(name = "timezone")
    private String timezone;
    
    @Column(name = "frequency_limit_per_day")
    private Integer frequencyLimitPerDay;
    
    @Column(name = "frequency_limit_per_hour")
    private Integer frequencyLimitPerHour;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    // Business methods
    public boolean isInQuietHours(LocalTime currentTime) {
        if (quietHoursStart == null || quietHoursEnd == null) {
            return false;
        }
        
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            // Same day quiet hours (e.g., 22:00 to 08:00 next day)
            return currentTime.isAfter(quietHoursStart) || currentTime.isBefore(quietHoursEnd);
        } else {
            // Overnight quiet hours (e.g., 08:00 to 22:00)
            return currentTime.isAfter(quietHoursStart) && currentTime.isBefore(quietHoursEnd);
        }
    }
    
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
