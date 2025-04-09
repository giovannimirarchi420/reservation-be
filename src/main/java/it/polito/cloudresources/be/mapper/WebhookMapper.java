package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.webhooks.WebhookConfigDTO;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.model.WebhookConfig;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import it.polito.cloudresources.be.service.KeycloakService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Mapper for converting between WebhookConfig entity and DTO
 */
@Component
@RequiredArgsConstructor
public class WebhookMapper {

    private final ResourceRepository resourceRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final KeycloakService keycloakService;
    
    /**
     * Convert from DTO to entity
     */
    public WebhookConfig toEntity(WebhookConfigDTO dto) {
        if (dto == null) {
            return null;
        }
        
        WebhookConfig entity = new WebhookConfig();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setUrl(dto.getUrl());
        entity.setEventType(dto.getEventType());
        entity.setEnabled(dto.isEnabled());
        entity.setIncludeSubResources(dto.isIncludeSubResources());
        entity.setMaxRetries(dto.getMaxRetries());
        entity.setRetryDelaySeconds(dto.getRetryDelaySeconds());
        
        // Set resource if specified
        if (dto.getResourceId() != null) {
            Resource resource = resourceRepository.findById(dto.getResourceId())
                    .orElseThrow(() -> new EntityNotFoundException("Resource not found with ID: " + dto.getResourceId()));
            entity.setResource(resource);
        }
        
        // Set resource type if specified
        if (dto.getResourceTypeId() != null) {
            ResourceType resourceType = resourceTypeRepository.findById(dto.getResourceTypeId())
                    .orElseThrow(() -> new EntityNotFoundException("Resource type not found with ID: " + dto.getResourceTypeId()));
            entity.setResourceType(resourceType);
        }

        entity.setSiteId(dto.getSiteId());
        
        return entity;
    }
    
    /**
     * Convert from entity to DTO
     */
    public WebhookConfigDTO toDto(WebhookConfig entity) {
        if (entity == null) {
            return null;
        }
        
        WebhookConfigDTO dto = new WebhookConfigDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setUrl(entity.getUrl());
        dto.setEventType(entity.getEventType());
        dto.setEnabled(entity.isEnabled());
        dto.setIncludeSubResources(entity.isIncludeSubResources());
        dto.setMaxRetries(entity.getMaxRetries());
        dto.setRetryDelaySeconds(entity.getRetryDelaySeconds());
        
        // Set resource information
        if (entity.getResource() != null) {
            dto.setResourceId(entity.getResource().getId());
            dto.setResourceName(entity.getResource().getName());
        }
        
        // Set resource type information
        if (entity.getResourceType() != null) {
            dto.setResourceTypeId(entity.getResourceType().getId());
            dto.setResourceTypeName(entity.getResourceType().getName());
        }


        Optional<GroupRepresentation> groupRepresentation = keycloakService.getGroupById(entity.getSiteId());
        if(groupRepresentation.isPresent()) {
            dto.setSiteId(entity.getSiteId());
            dto.setSiteName(groupRepresentation.get().getName());
        } else {
            throw new RuntimeException("No group found");
        }

        
        return dto;
    }
}