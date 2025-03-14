package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.EventDTO;
import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.User;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.UserRepository;
import it.polito.cloudresources.be.util.DateTimeUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Event and EventDTO objects
 */
@Component
@Slf4j
public class EventMapper implements EntityMapper<EventDTO, Event> {
    
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final DateTimeUtils dateTimeUtils;
    
    public EventMapper(ResourceRepository resourceRepository, 
                       UserRepository userRepository,
                       DateTimeUtils dateTimeUtils) {
        this.resourceRepository = resourceRepository;
        this.userRepository = userRepository;
        this.dateTimeUtils = dateTimeUtils;
    }
    
    @Override
    public Event toEntity(EventDTO dto) {
        if (dto == null) {
            return null;
        }
        
        Event event = new Event();
        
        // Copy the simple fields
        event.setId(dto.getId());
        event.setTitle(dto.getTitle());
        event.setDescription(dto.getDescription());
        
        // Ensure dates have the correct timezone
        if (dto.getStart() != null) {
            event.setStart(dateTimeUtils.ensureTimeZone(dto.getStart()));
        }
        
        if (dto.getEnd() != null) {
            event.setEnd(dateTimeUtils.ensureTimeZone(dto.getEnd()));
        }
        
        // Set the resource
        if (dto.getResourceId() != null) {
            Resource resource = resourceRepository.findById(dto.getResourceId())
                    .orElseThrow(() -> new EntityNotFoundException("Resource not found with ID: " + dto.getResourceId()));
            event.setResource(resource);
        }
        
        // Set the user by Keycloak ID
        if (dto.getUserId() != null) {
            log.debug("Looking for user with Keycloak ID: {}", dto.getUserId());
            User user = userRepository.findByKeycloakId(dto.getUserId())
                    .orElseThrow(() -> new EntityNotFoundException("User not found with Keycloak ID: " + dto.getUserId()));
            event.setUser(user);
        }
        
        return event;
    }
    
    @Override
    public EventDTO toDto(Event entity) {
        if (entity == null) {
            return null;
        }
        
        EventDTO dto = new EventDTO();
        
        // Copy the simple fields
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setStart(entity.getStart());
        dto.setEnd(entity.getEnd());
        
        // Copy the relation fields
        if (entity.getResource() != null) {
            dto.setResourceId(entity.getResource().getId());
            dto.setResourceName(entity.getResource().getName());
        }
        
        if (entity.getUser() != null) {
            dto.setUserId(entity.getUser().getKeycloakId());
            dto.setUserName(entity.getUser().getUsername());
        }
        
        return dto;
    }
}