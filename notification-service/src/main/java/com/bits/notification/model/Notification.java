package com.bits.notification.model;

import com.bits.notification.enums.NotificationChannel;
import com.bits.notification.enums.NotificationPriority;
import com.bits.notification.enums.NotificationStatus;
import com.bits.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_user", columnList = "user_id"),
    @Index(name = "idx_notification_type", columnList = "type"),
    @Index(name = "idx_notification_status", columnList = "status"),
    @Index(name = "idx_notification_scheduled", columnList = "scheduled_at"),
    @Index(name = "idx_notification_priority", columnList = "priority")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "notification_id", unique = true, nullable = false)
    private String notificationId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "user_email")
    private String userEmail;
    
    @Column(name = "user_phone")
    private String userPhone;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;
    
    @ElementCollection
    @CollectionTable(name = "notification_channels", joinColumns = @JoinColumn(name = "notification_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "channel")
    @Builder.Default
    private List<NotificationChannel> channels = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "notification_metadata", joinColumns = @JoinColumn(name = "notification_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;
    
    @Column(name = "template_id")
    private String templateId;
    
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "max_retries")
    @Builder.Default
    private Integer maxRetries = 3;
    
    @Column(name = "error_message")
    private String errorMessage;
    
    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<NotificationDelivery> deliveries = new ArrayList<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    // Business methods
    public boolean canRetry() {
        return retryCount < maxRetries && 
               (status == NotificationStatus.FAILED || status == NotificationStatus.PENDING);
    }
    
    public void incrementRetryCount() {
        this.retryCount++;
    }
    
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    public void addDelivery(NotificationDelivery delivery) {
        deliveries.add(delivery);
        delivery.setNotification(this);
    }
    
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
    
    public List<NotificationDelivery> getDeliveries() { return deliveries; }
    public void setDeliveries(List<NotificationDelivery> deliveries) { this.deliveries = deliveries; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    
    // Alias methods for service compatibility
    public String getContent() { return message; }
    public void setContent(String content) { this.message = content; }
    
    // For compatibility with delivery tracking
    public LocalDateTime getSentAt() {
        // Return the earliest sent delivery time or null
        return deliveries.stream()
            .filter(d -> d.getSentAt() != null)
            .map(NotificationDelivery::getSentAt)
            .min(LocalDateTime::compareTo)
            .orElse(null);
    }
    
    public LocalDateTime getDeliveredAt() {
        // Return the earliest delivered delivery time or null
        return deliveries.stream()
            .filter(d -> d.getDeliveredAt() != null)
            .map(NotificationDelivery::getDeliveredAt)
            .min(LocalDateTime::compareTo)
            .orElse(null);
    }
    
    public void setSentAt(LocalDateTime sentAt) {
        // This is a computed field, but we can update status if needed
        if (sentAt != null && this.status == NotificationStatus.PENDING) {
            this.status = NotificationStatus.SENT;
        }
    }
}
