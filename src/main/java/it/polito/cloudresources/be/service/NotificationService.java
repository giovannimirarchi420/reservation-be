package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.NotificationDTO;
import it.polito.cloudresources.be.mapper.NotificationMapper;
import it.polito.cloudresources.be.model.Notification;
import it.polito.cloudresources.be.model.NotificationType;
import it.polito.cloudresources.be.model.User;
import it.polito.cloudresources.be.repository.NotificationRepository;
import it.polito.cloudresources.be.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for notification operations
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final AuditLogService auditLogService;

    /**
     * Get all notifications for a user
     */
    public List<NotificationDTO> getUserNotifications(Long userId) {
        return notificationMapper.toDto(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
        );
    }

    /**
     * Get unread notifications for a user
     */
    public List<NotificationDTO> getUnreadNotifications(Long userId) {
        return notificationMapper.toDto(
                notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false)
        );
    }

    /**
     * Get the count of unread notifications for a user
     */
    public int getUnreadNotificationCount(Long userId) {
        return notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false).size();
    }

    /**
     * Mark a notification as read
     */
    @Transactional
    public Optional<NotificationDTO> markAsRead(Long notificationId, Long userId) {
        return notificationRepository.findById(notificationId)
                .filter(notification -> notification.getUser().getId().equals(userId))
                .map(notification -> {
                    notification.setRead(true);
                    return notificationMapper.toDto(notificationRepository.save(notification));
                });
    }

    /**
     * Mark all notifications as read for a user
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        List<Notification> notifications = notificationRepository.findByUserIdAndReadOrderByCreatedAtDesc(userId, false);
        for (Notification notification : notifications) {
            notification.setRead(true);
            notificationRepository.save(notification);
        }
        
        // Log the action
        auditLogService.logUserAction("notification", "Mark all as read for user ID: " + userId);
    }

    /**
     * Delete a notification
     */
    @Transactional
    public boolean deleteNotification(Long notificationId, Long userId) {
        Optional<Notification> notification = notificationRepository.findById(notificationId);
        if (notification.isPresent() && notification.get().getUser().getId().equals(userId)) {
            notificationRepository.delete(notification.get());
            
            // Log the action
            auditLogService.logUserAction("notification", "Delete notification ID: " + notificationId);
            
            return true;
        }
        return false;
    }

    /**
     * Create a notification for a user
     */
    @Transactional
    public NotificationDTO createNotification(Long userId, String message, String type) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        
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
        notification.setUser(user);
        
        Notification savedNotification = notificationRepository.save(notification);
        return notificationMapper.toDto(savedNotification);
    }

    /**
     * Create a notification for all users with a specific role
     */
    @Transactional
    public void createNotificationForRole(String role, String message, String type) {
        List<User> users = userRepository.findByRolesContaining(role);
        
        NotificationType notificationType;
        try {
            notificationType = NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            notificationType = NotificationType.INFO;
        }
        
        for (User user : users) {
            Notification notification = new Notification();
            notification.setMessage(message);
            notification.setType(notificationType);
            notification.setRead(false);
            notification.setUser(user);
            
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
        List<User> users = userRepository.findAll();
        
        NotificationType notificationType;
        try {
            notificationType = NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            notificationType = NotificationType.INFO;
        }
        
        for (User user : users) {
            Notification notification = new Notification();
            notification.setMessage(message);
            notification.setType(notificationType);
            notification.setRead(false);
            notification.setUser(user);
            
            notificationRepository.save(notification);
        }
        
        // Log the action
        auditLogService.logSystemEvent("Global notification", "Created global notification: " + message);
    }
}