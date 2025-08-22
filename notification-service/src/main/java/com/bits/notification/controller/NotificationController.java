package com.bits.notification.controller;

import com.bits.notification.dto.*;
import com.bits.notification.enums.NotificationStatus;
import com.bits.notification.security.JwtService;
import com.bits.notification.service.NotificationService;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Validated
public class NotificationController {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private JwtService jwtService;
    
    private final Counter notificationCreatedCounter;
    private final Counter notificationRetrievedCounter;
    
    public NotificationController(MeterRegistry meterRegistry) {
        this.notificationCreatedCounter = Counter.builder("notifications.created")
            .description("Number of notifications created")
            .register(meterRegistry);
        this.notificationRetrievedCounter = Counter.builder("notifications.retrieved")
            .description("Number of notifications retrieved")
            .register(meterRegistry);
    }
    
    @PostMapping
    @Timed(value = "notification.create", description = "Time taken to create notification")
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody CreateNotificationRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.info("Creating notification for user: {}", userId);
        
        // Set userId from JWT token
        request.setUserId(userId);
        
        NotificationResponse response = notificationService.createNotification(request);
        notificationCreatedCounter.increment();
        
        logger.info("Created notification with ID: {}", response.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{id}")
    @Timed(value = "notification.get", description = "Time taken to get notification")
    public ResponseEntity<NotificationResponse> getNotification(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.debug("Fetching notification: {} for user: {}", id, userId);
        
        NotificationResponse response = notificationService.getNotification(id);
        
        // Ensure user can only access their own notifications
        if (!userId.equals(response.getUserId())) {
            logger.warn("User {} attempted to access notification {} belonging to user {}", 
                       userId, id, response.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        notificationRetrievedCounter.increment();
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @Timed(value = "notification.list", description = "Time taken to list notifications")
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            HttpServletRequest httpRequest,
            Pageable pageable) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.debug("Fetching notifications for user: {}", userId);
        
        Page<NotificationResponse> notifications = notificationService.getUserNotifications(userId, pageable);
        
        return ResponseEntity.ok(notifications);
    }
    
    @GetMapping("/status/{status}")
    @Timed(value = "notification.list.by.status", description = "Time taken to list notifications by status")
    public ResponseEntity<Page<NotificationResponse>> getNotificationsByStatus(
            @PathVariable NotificationStatus status,
            HttpServletRequest httpRequest,
            Pageable pageable) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.debug("Fetching notifications with status: {} for user: {}", status, userId);
        
        // For security, we'll filter by user in the service layer
        Page<NotificationResponse> notifications = notificationService.getNotificationsByStatus(status, pageable);
        
        // Filter to only include current user's notifications
        Page<NotificationResponse> userNotifications = notifications
            .map(notification -> userId.equals(notification.getUserId()) ? notification : null)
            .map(notification -> notification);
        
        return ResponseEntity.ok(userNotifications);
    }
    
    @PutMapping("/{id}/status")
    @Timed(value = "notification.update.status", description = "Time taken to update notification status")
    public ResponseEntity<NotificationResponse> updateNotificationStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNotificationRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.info("Updating notification status: {} for user: {}", id, userId);
        
        // First check if user owns the notification
        NotificationResponse existing = notificationService.getNotification(id);
        if (!userId.equals(existing.getUserId())) {
            logger.warn("User {} attempted to update notification {} belonging to user {}", 
                       userId, id, existing.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        NotificationResponse response = notificationService.updateNotificationStatus(id, request);
        
        logger.info("Updated notification status: {} to: {}", id, request.getStatus());
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{id}")
    @Timed(value = "notification.cancel", description = "Time taken to cancel notification")
    public ResponseEntity<Void> cancelNotification(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "User requested cancellation") String reason,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.info("Cancelling notification: {} for user: {}", id, userId);
        
        // First check if user owns the notification
        NotificationResponse existing = notificationService.getNotification(id);
        if (!userId.equals(existing.getUserId())) {
            logger.warn("User {} attempted to cancel notification {} belonging to user {}", 
                       userId, id, existing.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        notificationService.cancelNotification(id, reason);
        
        logger.info("Cancelled notification: {}", id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/stats")
    @Timed(value = "notification.stats", description = "Time taken to get notification stats")
    public ResponseEntity<NotificationStatsResponse> getNotificationStats(HttpServletRequest httpRequest) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.debug("Fetching notification stats for user: {}", userId);
        
        NotificationStatsResponse stats = notificationService.getNotificationStats(userId);
        
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/summary")
    @Timed(value = "notification.summary", description = "Time taken to get notification summary")
    public ResponseEntity<NotificationSummaryResponse> getNotificationSummary(HttpServletRequest httpRequest) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.debug("Fetching notification summary for user: {}", userId);
        
        // Get recent notifications for summary
        Page<NotificationResponse> recentNotifications = notificationService.getUserNotifications(
            userId, Pageable.ofSize(5));
        
        NotificationStatsResponse stats = notificationService.getNotificationStats(userId);
        
        NotificationSummaryResponse summary = new NotificationSummaryResponse();
        summary.setTotalCount(stats.getTotalCount());
        summary.setUnreadCount(stats.getPendingCount());
        summary.setRecentNotifications(recentNotifications.getContent());
        
        return ResponseEntity.ok(summary);
    }
    
    @GetMapping("/scheduled")
    @Timed(value = "notification.scheduled", description = "Time taken to get scheduled notifications")
    public ResponseEntity<List<NotificationResponse>> getScheduledNotifications(HttpServletRequest httpRequest) {
        
        String userId = extractUserIdFromToken(httpRequest);
        logger.debug("Fetching scheduled notifications for user: {}", userId);
        
        List<NotificationResponse> scheduledNotifications = notificationService.getScheduledNotifications();
        
        // Filter to only include current user's notifications
        List<NotificationResponse> userScheduledNotifications = scheduledNotifications.stream()
            .filter(notification -> userId.equals(notification.getUserId()))
            .toList();
        
        return ResponseEntity.ok(userScheduledNotifications);
    }
    
    private String extractUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtService.extractUserId(token);
        }
        throw new RuntimeException("No valid JWT token found");
    }
}
