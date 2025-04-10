package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.ResourceDTO;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import it.polito.cloudresources.be.service.KeycloakService;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;

import org.keycloak.representations.idm.GroupRepresentation;

import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Resource and ResourceDTO objects
 */
@Component
@AllArgsConstructor
public class ResourceMapper implements EntityMapper<ResourceDTO, Resource> {
    
    private final ResourceTypeRepository resourceTypeRepository;
    private final ResourceRepository resourceRepository;
    private final KeycloakService keycloakService;
    
    @Override
    public Resource toEntity(ResourceDTO dto) {
        if (dto == null) {
            return null;
        }
        
        Resource resource = new Resource();
        resource.setId(dto.getId());
        resource.setName(dto.getName());
        resource.setSpecs(dto.getSpecs());
        resource.setLocation(dto.getLocation());
        resource.setStatus(dto.getStatus());
        resource.setSiteId(dto.getSiteId());
        
        // Set the resource type
        if (dto.getTypeId() != null) {
            ResourceType type = resourceTypeRepository.findById(dto.getTypeId())
                    .orElseThrow(() -> new EntityNotFoundException("Resource type not found with ID: " + dto.getTypeId()));
            resource.setType(type);
        }

        if (dto.getParentId() != null) {
            resourceRepository.findById(dto.getParentId()).ifPresent(resource::setParent);
        }
        
        return resource;
    }
    
    @Override
    public ResourceDTO toDto(Resource entity) {
        if (entity == null) {
            return null;
        }
        
        ResourceDTO dto = new ResourceDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setSpecs(entity.getSpecs());
        dto.setLocation(entity.getLocation());
        dto.setStatus(entity.getStatus());
        dto.setSiteId(entity.getSiteId());
        
        // Set type information
        if (entity.getType() != null) {
            dto.setTypeId(entity.getType().getId());
            dto.setTypeName(entity.getType().getName());
            dto.setTypeColor(entity.getType().getColor());
        }
        
        // Set parent information
        if (entity.getParent() != null) {
            dto.setParentId(entity.getParent().getId());
            dto.setParentName(entity.getParent().getName());
        }

        // Set sub-resources
        dto.setSubResourceIds(entity.getSubResources().stream()
            .map(Resource::getId)
            .collect(Collectors.toList()));
        
        // Set site name from Keycloak
        if (entity.getSiteId() != null) {
            Optional<GroupRepresentation> site = keycloakService.getGroupById(entity.getSiteId());
            site.ifPresent(group -> dto.setSiteName(group.getName()));
        }
        
        return dto;
    }
}