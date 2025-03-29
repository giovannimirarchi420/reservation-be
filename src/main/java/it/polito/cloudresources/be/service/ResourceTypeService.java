package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.ResourceTypeDTO;
import it.polito.cloudresources.be.mapper.ResourceTypeMapper;
import it.polito.cloudresources.be.model.AuditLog;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for resource type operations
 */
@Service
@RequiredArgsConstructor
public class ResourceTypeService {

    private final ResourceTypeRepository resourceTypeRepository;
    private final ResourceRepository resourceRepository;
    private final KeycloakService keycloakService;
    private final ResourceTypeMapper resourceTypeMapper;
    private final AuditLogService auditLogService;

    /**
     * Get all resource types filtered by federations
     */
    public List<ResourceTypeDTO> getAllResourceTypes(List<String> federationIds) {
        return resourceTypeMapper.toDto(resourceTypeRepository.findByFederationIdIn(federationIds));
    }

    /**
     * Get all resource types
     */
    public List<ResourceTypeDTO> getAllResourceTypes() {
        return resourceTypeMapper.toDto(resourceTypeRepository.findAll());
    }

    /**
     * Get resource type by ID
     */
    public Optional<ResourceTypeDTO> getResourceTypeById(Long id) {
        return resourceTypeRepository.findById(id)
                .map(resourceTypeMapper::toDto);
    }

    /**
     * Create new resource type
     */
    @Transactional
    public ResourceTypeDTO createResourceType(ResourceTypeDTO resourceTypeDTO, String userId) {
        if (!canUpdateResourceTypeInFederation(userId, resourceTypeDTO.getFederationId())) {
            throw new AccessDeniedException("You don't have permission to create resource types in this federation");
        }

        ResourceType resourceType = resourceTypeMapper.toEntity(resourceTypeDTO);
        ResourceType savedType = resourceTypeRepository.save(resourceType);

        auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                AuditLog.LogAction.CREATE,
                new AuditLog.LogEntity("RESOURCE-TYPE", savedType.getId().toString()),
                "Admin " + userId + " created resource type: "+ savedType);

        return resourceTypeMapper.toDto(savedType);
    }

    /**
     * Update existing resource type
     */
    @Transactional
    public Optional<ResourceTypeDTO> updateResourceType(Long id, ResourceTypeDTO resourceTypeDTO, String userId) {
        return resourceTypeRepository.findById(id)
                .map(existingType -> {
                    if (!canUpdateResourceTypeInFederation(userId, existingType.getFederationId())) {
                        throw new AccessDeniedException("You don't have permission to create resource types in this federation");
                    }

                    // Apply updates from DTO to entity
                    existingType.setName(resourceTypeDTO.getName());
                    existingType.setColor(resourceTypeDTO.getColor());
                    
                    // Save updated entity
                    ResourceType updatedType = resourceTypeRepository.save(existingType);

                    auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                            AuditLog.LogAction.UPDATE,
                            new AuditLog.LogEntity("RESOURCE-TYPE", updatedType.getId().toString()),
                            "Admin " + userId + " updated resource type to: " + updatedType);
                    
                    return resourceTypeMapper.toDto(updatedType);
                });
    }

    /**
     * Delete resource type
     * Returns false if the type doesn't exist or is in use or the user does not have the grants
     */
    @Transactional
    public boolean deleteResourceType(Long id, String userId) {

        Optional<ResourceType> resourceType = resourceTypeRepository.findById(id);

        // Check if the resource type exists
        if (!resourceType.isPresent()) {
            return false;
        }
        
        // Check if the resource type is in use
        if (!resourceRepository.findByTypeId(id).isEmpty()) {
            return false;
        }

        if (!canUpdateResourceTypeInFederation(userId, resourceType.get().getFederationId())) {
            throw new AccessDeniedException("You don't have permission to delete resource types in this federation");
        }
        
        // Get type name for logging before deletion
        String typeName = resourceType.get().getName();
        
        // Delete the resource type
        resourceTypeRepository.deleteById(id);

        auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                AuditLog.LogAction.DELETE,
                new AuditLog.LogEntity("RESOURCE-TYPE", id.toString()),
                "Admin " + userId + " deleted resource type: " +resourceType.get() );
        
        return true;
    }

    private boolean canUpdateResourceTypeInFederation(String userId, String federationId) {
        // Global admins can create resources in any federation
        if (keycloakService.hasGlobalAdminRole(userId)) {
            return true;
        }
        
        // Federation admins can create resources only in their federations
        if (keycloakService.isUserFederationAdmin(userId, federationId)) {
            return true;
        }
        
        return false;
    }
}