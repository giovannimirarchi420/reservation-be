package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.NotificationDTO;
import it.polito.cloudresources.be.model.Notification;
import it.polito.cloudresources.be.model.User;
import it.polito.cloudresources.be.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Notification and NotificationDTO objects
 */
@Component
public class NotificationMapper implements EntityMapper<NotificationDTO, Notification> {
    
    private final UserRepository userRepository;
    
    public NotificationMapper(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
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
        
        // Note: We don't set the user here because we typically don't
        // need to convert from DTO to entity for notifications
        // That's handled in the service layer directly
        
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
     * Creates a notification entity for a specific user
     *
     * @param dto the notification DTO
     * @param userId the user ID
     * @return the notification entity
     */
    public Notification createNotificationForUser(NotificationDTO dto, Long userId) {
        Notification notification = toEntity(dto);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));
                
        notification.setUser(user);
        
        return notification;
    }
}