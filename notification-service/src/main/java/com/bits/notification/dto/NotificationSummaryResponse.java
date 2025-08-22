package com.bits.notification.dto;

import com.bits.notification.enums.NotificationPriority;
import com.bits.notification.enums.NotificationStatus;
import com.bits.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSummaryResponse {
    
    private Long id;
    private String notificationId;
    private NotificationType type;
    private NotificationPriority priority;
    private String title;
    private NotificationStatus status;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt;
    
    // Summary fields
    private Long totalCount;
    private Long unreadCount;
    private List<NotificationResponse> recentNotifications;
    
    // Manual getters and setters for compatibility
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
    
    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }
    
    public NotificationPriority getPriority() { return priority; }
    public void setPriority(NotificationPriority priority) { this.priority = priority; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    // Summary field getters and setters
    public Long getTotalCount() { return totalCount; }
    public void setTotalCount(Long totalCount) { this.totalCount = totalCount; }
    
    public Long getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Long unreadCount) { this.unreadCount = unreadCount; }
    
    public List<NotificationResponse> getRecentNotifications() { return recentNotifications; }
    public void setRecentNotifications(List<NotificationResponse> recentNotifications) { this.recentNotifications = recentNotifications; }
}
