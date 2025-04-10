package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.ResourceTypeDTO;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.service.KeycloakService;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Mapper for converting between ResourceType and ResourceTypeDTO objects
 */
@Component
public class ResourceTypeMapper implements EntityMapper<ResourceTypeDTO, ResourceType> {
    
    private final KeycloakService keycloakService;
    
    public ResourceTypeMapper(KeycloakService keycloakService) {
        this.keycloakService = keycloakService;
    }
    
    @Override
    public ResourceType toEntity(ResourceTypeDTO dto) {
        if (dto == null) {
            return null;
        }
        
        ResourceType resourceType = new ResourceType();
        resourceType.setId(dto.getId());
        resourceType.setName(dto.getName());
        resourceType.setColor(dto.getColor());
        resourceType.setSiteId(dto.getSiteId());
        
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
        dto.setSiteId(entity.getSiteId());
        
        // Set site name from Keycloak
        if (entity.getSiteId() != null) {
            Optional<GroupRepresentation> site = keycloakService.getGroupById(entity.getSiteId());
            site.ifPresent(group -> dto.setSiteName(group.getName()));
        }
        
        return dto;
    }
}