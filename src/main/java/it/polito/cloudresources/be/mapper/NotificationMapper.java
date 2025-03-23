package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.NotificationDTO;
import it.polito.cloudresources.be.model.Notification;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Notification and NotificationDTO objects
 * Now working with Keycloak IDs instead of User entities
 */
@Component
public class NotificationMapper implements EntityMapper<NotificationDTO, Notification> {
    
    @Override
    public Notification toEntity(NotificationDTO dto) {
        if (dto == null) {
            return null;
        }
        
        Notification notification = new Notification();
        notification.setId(dto.getId());
        notification.setMessage(dto.getMessage());
        notification.setType(dto.getType());
        notification.setRead(dto.isRead());
        
        // Note: We don't set the keycloakId here because it's typically
        // set in the service layer directly during notification creation
        
        return notification;
    }
    
    @Override
    public NotificationDTO toDto(Notification entity) {
        if (entity == null) {
            return null;
        }
        
        NotificationDTO dto = new NotificationDTO();
        dto.setId(entity.getId());
        dto.setMessage(entity.getMessage());
        dto.setType(entity.getType());
        dto.setRead(entity.isRead());
        dto.setCreatedAt(entity.getCreatedAt());
        
        return dto;
    }
    
    /**
     * Creates a notification entity for a specific user by Keycloak ID
     */
    public Notification createNotificationForUser(NotificationDTO dto, String keycloakId) {
        Notification notification = toEntity(dto);
        notification.setKeycloakId(keycloakId);
        return notification;
    }
}