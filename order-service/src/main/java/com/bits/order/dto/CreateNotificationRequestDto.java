package com.bits.order.dto;

import com.bits.order.enums.NotificationChannel;
import com.bits.order.enums.NotificationPriority;
import com.bits.order.enums.NotificationType;
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
public class CreateNotificationRequestDto {
    
    private String userId;
    private String userEmail;
    private String userPhone;
    private NotificationType type;
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.NORMAL;
    private String title;
    private String message;
    private List<NotificationChannel> channels;
    private Map<String, String> metadata;
    private String templateId;
    private LocalDateTime scheduledAt;
    private LocalDateTime expiresAt;
    private Integer maxRetries;
}
