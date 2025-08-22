package com.bits.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponseDto {
    
    private Long id;
    private String userId;
    private String type;
    private String title;
    private String message;
    private String status;
    private String priority;
    private LocalDateTime createdAt;
    private LocalDateTime scheduledAt;
}
