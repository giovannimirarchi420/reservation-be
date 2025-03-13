package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.ResourceDTO;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between Resource and ResourceDTO objects
 */
@Component
public class ResourceMapper implements EntityMapper<ResourceDTO, Resource> {
    
    private final ResourceTypeRepository resourceTypeRepository;
    
    public ResourceMapper(ResourceTypeRepository resourceTypeRepository) {
        this.resourceTypeRepository = resourceTypeRepository;
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
        
        // Set the resource type
        if (dto.getTypeId() != null) {
            ResourceType type = resourceTypeRepository.findById(dto.getTypeId())
                    .orElseThrow(() -> new EntityNotFoundException("Resource type not found with ID: " + dto.getTypeId()));
            resource.setType(type);
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
        
        // Set type information
        if (entity.getType() != null) {
            dto.setTypeId(entity.getType().getId());
            dto.setTypeName(entity.getType().getName());
            dto.setTypeColor(entity.getType().getColor());
        }
        
        return dto;
    }
}