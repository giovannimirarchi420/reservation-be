package it.polito.cloudresources.be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polito.cloudresources.be.dto.NotificationDTO;
import it.polito.cloudresources.be.dto.WebhookNotificationRequestDTO;
import it.polito.cloudresources.be.mapper.NotificationMapper;
import it.polito.cloudresources.be.model.AuditLog;
import it.polito.cloudresources.be.model.Notification;
import it.polito.cloudresources.be.model.NotificationType;
import it.polito.cloudresources.be.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for notification operations with Keycloak integration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final KeycloakService keycloakService;
    private final NotificationMapper notificationMapper;
    private final AuditLogService auditLogService;
    private final WebhookService webhookService;

    /**
     * Get all notifications for a user by Keycloak ID
     */
    public List<NotificationDTO> getUserNotifications(String keycloakId) {
        return notificationRepository.findByKeycloakIdOrderByCreatedAtDesc(keycloakId).stream()
                .map(notificationMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get unread notifications for a user by Keycloak ID
     */
    public List<NotificationDTO> getUnreadNotifications(String keycloakId) {
        return notificationRepository.findByKeycloakIdAndReadOrderByCreatedAtDesc(keycloakId, false).stream()
                .map(notificationMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get the count of unread notifications for a user by Keycloak ID
     */
    public int getUnreadNotificationCount(String keycloakId) {
        return notificationRepository.countByKeycloakIdAndRead(keycloakId, false);
    }

    /**
     * Mark a notification as read
     */
    @Transactional
    public Optional<NotificationDTO> markAsRead(Long notificationId, String keycloakId) {
        return notificationRepository.findById(notificationId)
                .filter(notification -> notification.getKeycloakId().equals(keycloakId))
                .map(notification -> {
                    notification.setRead(true);
                    auditLogService.logCrudAction(AuditLog.LogType.USER,
                            AuditLog.LogAction.UPDATE,
                            new AuditLog.LogEntity("NOTIFICATION", notificationId.toString()),
                            "User: " + keycloakId + " marked notification as read");
                    return notificationMapper.toDto(notificationRepository.save(notification));
                });
    }

    /**
     * Mark all notifications as read for a user by Keycloak ID
     */
    @Transactional
    public void markAllAsRead(String keycloakId) {
        List<Notification> notifications = notificationRepository.findByKeycloakIdAndReadOrderByCreatedAtDesc(keycloakId, false);
        LinkedList<String> ids = new LinkedList<>();
        for (Notification notification : notifications) {
            notification.setRead(true);
            notificationRepository.save(notification);
            ids.add(notification.getId().toString());
        }
        auditLogService.logCrudAction(AuditLog.LogType.USER,
                AuditLog.LogAction.UPDATE,
                new AuditLog.LogEntity("NOTIFICATION", null),
                "User: " + keycloakId + " marked all notification as read (notification ids: " + ids + ")");

    }

    /**
     * Delete a notification
     */
    @Transactional
    public boolean deleteNotification(Long notificationId, String keycloakId) {
        Optional<Notification> notification = notificationRepository.findById(notificationId);
        if (notification.isPresent() && notification.get().getKeycloakId().equals(keycloakId)) {
            notificationRepository.delete(notification.get());

            auditLogService.logCrudAction(AuditLog.LogType.USER,
                    AuditLog.LogAction.DELETE,
                    new AuditLog.LogEntity("NOTIFICATION", notificationId.toString()),
                    "User: " + keycloakId + " deleted notification " + notificationId);
            
            return true;
        }
        return false;
    }

    /**
     * Create a notification for a user by Keycloak ID
     */
    @Transactional
    public NotificationDTO createNotification(String keycloakId, String message, String type) {
        // Verify the user exists in Keycloak
        keycloakService.getUserById(keycloakId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + keycloakId));
        
        NotificationType notificationType;
        try {
            notificationType = NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            notificationType = NotificationType.INFO;
        }
        
        Notification notification = new Notification();
        notification.setMessage(message);
        notification.setType(notificationType);
        notification.setRead(false);
        notification.setKeycloakId(keycloakId);
        
        Notification savedNotification = notificationRepository.save(notification);
        return notificationMapper.toDto(savedNotification);
    }

    /**
     * Create a notification for all users with a specific role
     */
    @Transactional
    public void createNotificationForRole(String role, String message, String type) {
        List<UserRepresentation> users = keycloakService.getUsersByRole(role);
        
        NotificationType notificationType;
        try {
            notificationType = NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            notificationType = NotificationType.INFO;
        }
        
        for (UserRepresentation user : users) {
            Notification notification = new Notification();
            notification.setMessage(message);
            notification.setType(notificationType);
            notification.setRead(false);
            notification.setKeycloakId(user.getId());
            
            notificationRepository.save(notification);
        }

    }

    /**
     * Create a system notification for all admins
     */
    @Transactional
    public void createSystemNotification(String message, String details) {
        createNotificationForRole("ADMIN", message + (details != null ? ": " + details : ""), "INFO");
    }

    /**
     * Create a notification for all users
     */
    @Transactional
    public void createGlobalNotification(String message, String type) {
        List<UserRepresentation> users = keycloakService.getUsers();
        
        NotificationType notificationType;
        try {
            notificationType = NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            notificationType = NotificationType.INFO;
        }
        
        for (UserRepresentation user : users) {
            Notification notification = new Notification();
            notification.setMessage(message);
            notification.setType(notificationType);
            notification.setRead(false);
            notification.setKeycloakId(user.getId());
            
            notificationRepository.save(notification);
        }

    }

    /**
     * Process webhook notification request with validation
     * 
     * @param rawPayload The raw payload as received from the webhook
     * @param signature The webhook signature from headers
     * @return ProcessWebhookResult containing the notification or error information
     */
    @Transactional
    public ProcessWebhookResult processWebhookNotification(String rawPayload, String signature) {
        try {
            // Parse the request body manually
            WebhookNotificationRequestDTO request;
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                request = objectMapper.readValue(rawPayload, WebhookNotificationRequestDTO.class);
            } catch (Exception e) {
                log.warn("Invalid JSON payload in webhook request", e);
                return ProcessWebhookResult.error(HttpStatus.BAD_REQUEST, "Invalid JSON payload");
            }
            
            // Validate required fields manually since we can't use @Valid
            if (request.getWebhookId() == null || request.getWebhookId().isBlank()) {
                return ProcessWebhookResult.error(HttpStatus.BAD_REQUEST, "Webhook ID is required");
            }
            if (request.getUserId() == null || request.getUserId().isBlank()) {
                return ProcessWebhookResult.error(HttpStatus.BAD_REQUEST, "User ID is required");
            }
            if (request.getMessage() == null || request.getMessage().isBlank()) {
                return ProcessWebhookResult.error(HttpStatus.BAD_REQUEST, "Message is required");
            }
            if (request.getType() == null || request.getType().isBlank()) {
                request.setType("INFO"); // Default value
            }
            
            // Validate signature using the raw payload
            if (!webhookService.validateWebhookSignature(request.getWebhookId(), signature, rawPayload)) {
                log.warn("Invalid webhook signature for webhook ID: {}", request.getWebhookId());
                return ProcessWebhookResult.error(HttpStatus.UNAUTHORIZED, "Invalid signature");
            }
            
            // Create notification
            NotificationDTO notification = createNotification(
                    request.getUserId(), 
                    request.getMessage(), 
                    request.getType()
            );
            
            log.info("Webhook notification created successfully for user {} from webhook {}", 
                    request.getUserId(), request.getWebhookId());
            
            return ProcessWebhookResult.success(notification);
            
        } catch (Exception e) {
            log.error("Error processing webhook notification: {}", e.getMessage(), e);
            return ProcessWebhookResult.error(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "An error occurred while processing the webhook notification");
        }
    }

    /**
     * Result class for webhook processing
     */
    public static class ProcessWebhookResult {
        private final boolean success;
        private final NotificationDTO notification;
        private final HttpStatus status;
        private final String errorMessage;

        private ProcessWebhookResult(boolean success, NotificationDTO notification, HttpStatus status, String errorMessage) {
            this.success = success;
            this.notification = notification;
            this.status = status;
            this.errorMessage = errorMessage;
        }

        public static ProcessWebhookResult success(NotificationDTO notification) {
            return new ProcessWebhookResult(true, notification, HttpStatus.OK, null);
        }

        public static ProcessWebhookResult error(HttpStatus status, String errorMessage) {
            return new ProcessWebhookResult(false, null, status, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public NotificationDTO getNotification() {
            return notification;
        }

        public HttpStatus getStatus() {
            return status;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}