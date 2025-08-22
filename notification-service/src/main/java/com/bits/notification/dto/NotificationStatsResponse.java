package com.bits.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationStatsResponse {
    
    private Long totalNotifications;
    private Long pendingNotifications;
    private Long sentNotifications;
    private Long deliveredNotifications;
    private Long failedNotifications;
    private Double deliveryRate;
    private Double failureRate;
    
    // Manual getters and setters for compatibility
    public Long getTotalNotifications() { return totalNotifications; }
    public void setTotalNotifications(Long totalNotifications) { this.totalNotifications = totalNotifications; }
    
    public Long getPendingNotifications() { return pendingNotifications; }
    public void setPendingNotifications(Long pendingNotifications) { this.pendingNotifications = pendingNotifications; }
    
    public Long getSentNotifications() { return sentNotifications; }
    public void setSentNotifications(Long sentNotifications) { this.sentNotifications = sentNotifications; }
    
    public Long getDeliveredNotifications() { return deliveredNotifications; }
    public void setDeliveredNotifications(Long deliveredNotifications) { this.deliveredNotifications = deliveredNotifications; }
    
    public Long getFailedNotifications() { return failedNotifications; }
    public void setFailedNotifications(Long failedNotifications) { this.failedNotifications = failedNotifications; }
    
    public Double getDeliveryRate() { return deliveryRate; }
    public void setDeliveryRate(Double deliveryRate) { this.deliveryRate = deliveryRate; }
    
    public Double getFailureRate() { return failureRate; }
    public void setFailureRate(Double failureRate) { this.failureRate = failureRate; }
    
    // Alias methods for service compatibility
    public void setTotalCount(long totalCount) { this.totalNotifications = totalCount; }
    public void setSentCount(long sentCount) { this.sentNotifications = sentCount; }
    public void setDeliveredCount(long deliveredCount) { this.deliveredNotifications = deliveredCount; }
    public void setFailedCount(long failedCount) { this.failedNotifications = failedCount; }
    public void setPendingCount(long pendingCount) { this.pendingNotifications = pendingCount; }
    
    // Alias getter methods for controller compatibility
    public Long getTotalCount() { return this.totalNotifications; }
    public Long getPendingCount() { return this.pendingNotifications; }
    public Long getSentCount() { return this.sentNotifications; }
    public Long getDeliveredCount() { return this.deliveredNotifications; }
    public Long getFailedCount() { return this.failedNotifications; }
}
