package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.ResourceDTO;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import jakarta.persistence.EntityNotFoundException;

import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Resource and ResourceDTO objects
 */
@Component
public class ResourceMapper implements EntityMapper<ResourceDTO, Resource> {
    
    private final ResourceTypeRepository resourceTypeRepository;
    private final ResourceRepository resourceRepository;
    
    public ResourceMapper(ResourceTypeRepository resourceTypeRepository, ResourceRepository resourceRepository) {
        this.resourceTypeRepository = resourceTypeRepository;
        this.resourceRepository = resourceRepository;
    }
    
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
        
        if (entity.getParent() != null) {
            dto.setParentId(entity.getParent().getId());
            dto.setParentName(entity.getParent().getName());
        }

        dto.setSubResourceIds(entity.getSubResources().stream()
            .map(Resource::getId)
            .collect(Collectors.toList()));
        
        return dto;
    }
}