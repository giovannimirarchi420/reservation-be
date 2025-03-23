package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.NotificationDTO;
import it.polito.cloudresources.be.mapper.NotificationMapper;
import it.polito.cloudresources.be.model.Notification;
import it.polito.cloudresources.be.model.NotificationType;
import it.polito.cloudresources.be.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
                    return notificationMapper.toDto(notificationRepository.save(notification));
                });
    }

    /**
     * Mark all notifications as read for a user by Keycloak ID
     */
    @Transactional
    public void markAllAsRead(String keycloakId) {
        List<Notification> notifications = notificationRepository.findByKeycloakIdAndReadOrderByCreatedAtDesc(keycloakId, false);
        for (Notification notification : notifications) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
        
        // Log the action
        auditLogService.logUserAction("notification", "Mark all as read for user ID: " + keycloakId);
    }

    /**
     * Delete a notification
     */
    @Transactional
    public boolean deleteNotification(Long notificationId, String keycloakId) {
        Optional<Notification> notification = notificationRepository.findById(notificationId);
        if (notification.isPresent() && notification.get().getKeycloakId().equals(keycloakId)) {
            notificationRepository.delete(notification.get());
            
            // Log the action
            auditLogService.logUserAction("notification", "Delete notification ID: " + notificationId);
            
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
        UserRepresentation user = keycloakService.getUserById(keycloakId)
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
        
        // Log the action
        auditLogService.logSystemEvent("Create role notification", 
                "Created notifications for role: " + role + " with message: " + message);
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
        
        // Log the action
        auditLogService.logSystemEvent("Global notification", "Created global notification: " + message);
    }
}