package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.ResourceTypeDTO;
import it.polito.cloudresources.be.model.ResourceType;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between ResourceType and ResourceTypeDTO objects
 */
@Component
public class ResourceTypeMapper implements EntityMapper<ResourceTypeDTO, ResourceType> {
    
    @Override
    public ResourceType toEntity(ResourceTypeDTO dto) {
        if (dto == null) {
            return null;
        }
        
        ResourceType resourceType = new ResourceType();
        resourceType.setId(dto.getId());
        resourceType.setName(dto.getName());
        resourceType.setColor(dto.getColor());
        resourceType.setFederationId(dto.getFederationId());
        
        return resourceType;
    }
    
    @Override
    public ResourceTypeDTO toDto(ResourceType entity) {
        if (entity == null) {
            return null;
        }
        
        ResourceTypeDTO dto = new ResourceTypeDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setColor(entity.getColor());
        dto.setFederationId(entity.getFederationId());
        
        return dto;
    }
}