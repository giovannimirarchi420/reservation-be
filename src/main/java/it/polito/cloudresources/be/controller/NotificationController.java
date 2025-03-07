package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.dto.NotificationDTO;
import it.polito.cloudresources.be.service.NotificationService;
import it.polito.cloudresources.be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API controller for managing user notifications
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "API for managing user notifications")
@SecurityRequirement(name = "bearer-auth")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    /**
     * Get current user's Keycloak ID from JWT token
     */
    private String getCurrentUserKeycloakId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            return jwtAuth.getToken().getSubject();
        }
        return null;
    }

    /**
     * Get current user's notifications
     */
    @GetMapping
    @Operation(summary = "Get user notifications", description = "Retrieves notifications for the current user")
    public ResponseEntity<List<NotificationDTO>> getMyNotifications(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            Authentication authentication) {
        
        String keycloakId = getCurrentUserKeycloakId(authentication);
        Long userId = userService.getUserIdByKeycloakId(keycloakId);
        
        List<NotificationDTO> notifications;
        if (unreadOnly) {
            notifications = notificationService.getUnreadNotifications(userId);
        } else {
            notifications = notificationService.getUserNotifications(userId);
        }
        
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get count of unread notifications
     */
    @GetMapping("/unread-count")
    @Operation(summary = "Get unread count", description = "Gets the count of unread notifications for the current user")
    public ResponseEntity<Integer> getUnreadCount(Authentication authentication) {
        String keycloakId = getCurrentUserKeycloakId(authentication);
        Long userId = userService.getUserIdByKeycloakId(keycloakId);
        
        int count = notificationService.getUnreadNotificationCount(userId);
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
        
        String keycloakId = getCurrentUserKeycloakId(authentication);
        Long userId = userService.getUserIdByKeycloakId(keycloakId);
        
        return notificationService.markAsRead(id, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Mark all notifications as read
     */
    @PatchMapping("/mark-all-read")
    @Operation(summary = "Mark all as read", description = "Marks all notifications as read for the current user")
    public ResponseEntity<ApiResponseDTO> markAllAsRead(Authentication authentication) {
        String keycloakId = getCurrentUserKeycloakId(authentication);
        Long userId = userService.getUserIdByKeycloakId(keycloakId);
        
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(new ApiResponseDTO(true, "All notifications marked as read"));
    }

    /**
     * Delete notification
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete notification", description = "Deletes a notification")
    public ResponseEntity<ApiResponseDTO> deleteNotification(
            @PathVariable Long id,
            Authentication authentication) {
        
        String keycloakId = getCurrentUserKeycloakId(authentication);
        Long userId = userService.getUserIdByKeycloakId(keycloakId);
        
        boolean deleted = notificationService.deleteNotification(id, userId);
        
        if (deleted) {
            return ResponseEntity.ok(new ApiResponseDTO(true, "Notification deleted successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponseDTO(false, "Notification not found or access denied"));
        }
    }

    /**
     * Send notification to a user (admin only)
     */
    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send notification", description = "Sends a notification to a specific user (Admin only)")
    public ResponseEntity<NotificationDTO> sendNotification(
            @RequestParam Long userId,
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "INFO") String type) {
        
        NotificationDTO notification = notificationService.createNotification(userId, message, type);
        return ResponseEntity.ok(notification);
    }
}
