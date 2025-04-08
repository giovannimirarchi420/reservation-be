package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.SiteDTO;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for converting between Keycloak GroupRepresentation (Site) and SiteDTO objects
 */
@Component
public class SiteMapper {
    
    /**
     * Convert from GroupRepresentation to SiteDTO
     */
    public SiteDTO toDto(GroupRepresentation group) {
        if (group == null) {
            return null;
        }
        
        SiteDTO dto = new SiteDTO();
        dto.setId(group.getId());
        dto.setName(group.getName());
        
        // Get description from attributes
        if (group.getAttributes() != null && group.getAttributes().containsKey("description")) {
            List<String> descriptions = group.getAttributes().get("description");
            if (!descriptions.isEmpty()) {
                dto.setDescription(descriptions.get(0));
            }
        }
        
        return dto;
    }
    
    /**
     * Convert a list of GroupRepresentations to a list of SiteDTOs
     */
    public List<SiteDTO> toDto(List<GroupRepresentation> groups) {
        if (groups == null) {
            return null;
        }
        
        return groups.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Convert SiteDTO to GroupRepresentation for creation/updates
     */
    public GroupRepresentation toEntity(SiteDTO dto) {
        if (dto == null) {
            return null;
        }
        
        GroupRepresentation group = new GroupRepresentation();
        group.setName(dto.getName());
        
        // Set description as attribute
        if (dto.getDescription() != null && !dto.getDescription().isEmpty()) {
            group.singleAttribute("description", dto.getDescription());
        }
        
        return group;
    }
}