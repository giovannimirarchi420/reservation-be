package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.EventDTO;
import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.util.DateTimeUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Event and EventDTO objects
 * Now using Keycloak IDs instead of User entities
 */
@Component
@Slf4j
public class EventMapper implements EntityMapper<EventDTO, Event> {
    
    private final ResourceRepository resourceRepository;
    private final KeycloakService keycloakService;
    private final DateTimeUtils dateTimeUtils;
    
    public EventMapper(ResourceRepository resourceRepository, 
                       KeycloakService keycloakService,
                       DateTimeUtils dateTimeUtils) {
        this.resourceRepository = resourceRepository;
        this.keycloakService = keycloakService;
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
            log.debug("Setting Keycloak ID: {}", dto.getUserId());
            event.setKeycloakId(dto.getUserId());
        }
        
        // Set custom parameters
        event.setCustomParameters(dto.getCustomParameters());
        
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
        
        // Set user information from Keycloak
        dto.setUserId(entity.getKeycloakId());
        
        // Set custom parameters
        dto.setCustomParameters(entity.getCustomParameters());
        
        // Try to get username from Keycloak
        try {
            UserRepresentation user = keycloakService.getUserById(entity.getKeycloakId())
                .orElse(null);
                
            if (user != null) {
                dto.setUserName(user.getUsername());
            }
        } catch (Exception e) {
            log.warn("Could not fetch username for Keycloak ID: {}", entity.getKeycloakId());
        }
        
        return dto;
    }
}