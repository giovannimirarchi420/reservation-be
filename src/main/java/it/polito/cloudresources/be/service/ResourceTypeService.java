package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.ResourceTypeDTO;
import it.polito.cloudresources.be.mapper.ResourceTypeMapper;
import it.polito.cloudresources.be.model.AuditLog;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
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
     * Get all resource types filtered by sites if not null, otherwise return all resource-types in user sites
     */
    public List<ResourceTypeDTO> getAllResourceTypes(String userId, String siteId) {
        if(Objects.nonNull(siteId) && !siteId.isEmpty()) {
            if (!keycloakService.isUserInGroup(userId, siteId) &&
                !keycloakService.hasGlobalAdminRole(userId)) {
                throw new AccessDeniedException("User can't access resource types in this site");
            }
            return resourceTypeMapper.toDto(resourceTypeRepository.findBySiteId(siteId));
        }

        if(keycloakService.hasGlobalAdminRole(userId)) {
            resourceTypeMapper.toDto(resourceTypeRepository.findAll());
        }

        List<String> userSiteIds = keycloakService.getUserSites(userId);
        return resourceTypeMapper.toDto(resourceTypeRepository.findBySiteIdIn(userSiteIds));
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
    public ResourceTypeDTO getResourceTypeById(Long id, String userId) {
        Optional<ResourceType> resourceTypeOpt = resourceTypeRepository.findById(id);
        if(!resourceTypeOpt.isPresent()) {
            throw new EntityNotFoundException("Resource type not found");
        }

        ResourceType resourceType = resourceTypeOpt.get();

        if (!keycloakService.isUserInGroup(userId, resourceType.getSiteId()) &&
            !keycloakService.hasGlobalAdminRole(userId)) {
            throw new AccessDeniedException("User can't access resource type in this site");
        }

        return resourceTypeMapper.toDto(resourceType);
    }

    /**
     * Create new resource type
     */
    @Transactional
    public ResourceTypeDTO createResourceType(ResourceTypeDTO resourceTypeDTO, String userId) {
        if (!canUpdateResourceTypeInSite(userId, resourceTypeDTO.getSiteId())) {
            throw new AccessDeniedException("User does not have permission to create resource types in this site");
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
    public ResourceTypeDTO updateResourceType(Long id, ResourceTypeDTO resourceTypeDTO, String userId) {
        Optional<ResourceType> existingTypeOpt = resourceTypeRepository.findById(id);
        if(!existingTypeOpt.isPresent()) {
            throw new EntityNotFoundException("Resource type not found");
        }

        ResourceType resourceType = existingTypeOpt.get();

        if (!canUpdateResourceTypeInSite(userId, resourceType.getSiteId())) {
            throw new AccessDeniedException("User does not have permission to create resource types in this site");
        }

        resourceType.setName(resourceTypeDTO.getName());
        resourceType.setColor(resourceTypeDTO.getColor());

        ResourceType updatedType = resourceTypeRepository.save(resourceType);

        auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                AuditLog.LogAction.UPDATE,
                new AuditLog.LogEntity("RESOURCE-TYPE", updatedType.getId().toString()),
                "Admin " + userId + " updated resource type to: " + updatedType);

        return resourceTypeMapper.toDto(updatedType);
    }

    /**
     * Delete resource type
     * Returns false if the type doesn't exist or is in use or the user does not have the grants
     */
    @Transactional
    public void deleteResourceType(Long id, String userId) {

        Optional<ResourceType> resourceType = resourceTypeRepository.findById(id);

        // Check if the resource type exists
        if (!resourceType.isPresent()) {
            throw new EntityNotFoundException("Resource type not found");
        }
        
        // Check if the resource type is in use
        if (!resourceRepository.findByTypeId(id).isEmpty()) {
            throw new IllegalStateException("A resource using this resource type exists, delete all resources using this resource type first.");
        }

        if (!canUpdateResourceTypeInSite(userId, resourceType.get().getSiteId())) {
            throw new AccessDeniedException("User does not have permission to delete resource types in this site");
        }
        
        // Delete the resource type
        resourceTypeRepository.deleteById(id);

        auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                AuditLog.LogAction.DELETE,
                new AuditLog.LogEntity("RESOURCE-TYPE", id.toString()),
                "Admin " + userId + " deleted resource type: " +resourceType.get() );
    }

    private boolean canUpdateResourceTypeInSite(String userId, String siteId) {
        // Global admins can create resources in any site
        if (keycloakService.hasGlobalAdminRole(userId)) {
            return true;
        }
        
        // Site admins can create resources only in their sites
        if (keycloakService.isUserSiteAdmin(userId, siteId)) {
            return true;
        }
        
        return false;
    }
}