package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.NotificationDTO;
import it.polito.cloudresources.be.service.NotificationService;
import it.polito.cloudresources.be.util.ControllerUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * REST API controller for managing user notifications
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "API for managing user notifications")
@SecurityRequirement(name = "bearer-auth")
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final ControllerUtils utils;

    /**
     * Get current user's notifications
     */
    @GetMapping
    @Operation(summary = "Get user notifications", description = "Retrieves notifications for the current user")
    public ResponseEntity<List<NotificationDTO>> getMyNotifications(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            Authentication authentication) {
        
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        if (keycloakId == null) {
            return ResponseEntity.ok(Arrays.asList());
        }
        
        List<NotificationDTO> notifications;
        if (unreadOnly) {
            notifications = notificationService.getUnreadNotifications(keycloakId);
        } else {
            notifications = notificationService.getUserNotifications(keycloakId);
        }
        
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get count of unread notifications
     */
    @GetMapping("/unread-count")
    @Operation(summary = "Get unread count", description = "Gets the count of unread notifications for the current user")
    public ResponseEntity<Integer> getUnreadCount(Authentication authentication) {
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        if (keycloakId == null) {
            return ResponseEntity.ok(0);
        }
        
        int count = notificationService.getUnreadNotificationCount(keycloakId);
        return ResponseEntity.ok(count);
    }

    /**
     * Mark notification as read
     */
    @PatchMapping("/{id}/mark-read")
    @Operation(summary = "Mark as read", description = "Marks a notification as read")
    public ResponseEntity<NotificationDTO> markAsRead(
            @PathVariable Long id,
            Authentication authentication) {
        
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        if (keycloakId == null) {
            return ResponseEntity.notFound().build();
        }
        
        return notificationService.markAsRead(id, keycloakId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Mark all notifications as read
     */
    @PatchMapping("/mark-all-read")
    @Operation(summary = "Mark all as read", description = "Marks all notifications as read for the current user")
    public ResponseEntity<Object> markAllAsRead(Authentication authentication) {
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        if (keycloakId == null) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
        }
        
        notificationService.markAllAsRead(keycloakId);
        return utils.createSuccessResponse("All notifications marked as read");
    }

    /**
     * Delete notification
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete notification", description = "Deletes a notification")
    public ResponseEntity<Object> deleteNotification(
            @PathVariable Long id,
            Authentication authentication) {
        
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        if (keycloakId == null) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
        }
        
        boolean deleted = notificationService.deleteNotification(id, keycloakId);
        
        if (deleted) {
            return utils.createSuccessResponse("Notification deleted successfully");
        } else {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, "Notification not found or access denied");
        }
    }

    /**
     * Send notification to a user by Keycloak ID (admin only)
     */
    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN')")
    @Operation(summary = "Send notification", description = "Sends a notification to a specific user (Admin only)")
    public ResponseEntity<NotificationDTO> sendNotification(
            @RequestParam String userId,
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "INFO") String type) {
        
        NotificationDTO notification = notificationService.createNotification(userId, message, type);
        return ResponseEntity.ok(notification);
    }

    /**
     * Webhook endpoint for sending notifications with signature validation
     */
    @PostMapping("/webhook")
    @Operation(summary = "Webhook notification", description = "Sends a notification via webhook with signature validation")
    public ResponseEntity<Object> webhookNotification(
            @RequestBody String rawPayload,
            HttpServletRequest httpRequest) {
        
        // Get signature from header
        String signature = httpRequest.getHeader("X-Webhook-Signature");
        if (signature == null || signature.isEmpty()) {
            log.warn("Missing X-Webhook-Signature header in webhook request");
            return utils.createErrorResponse(HttpStatus.UNAUTHORIZED, "Missing signature header");
        }
        
        // Process webhook notification using service
        NotificationService.ProcessWebhookResult result = notificationService.processWebhookNotification(rawPayload, signature);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(result.getNotification());
        } else {
            return utils.createErrorResponse(result.getStatus(), result.getErrorMessage());
        }
    }
}