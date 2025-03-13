package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.ResourceTypeDTO;
import it.polito.cloudresources.be.mapper.ResourceTypeMapper;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import lombok.RequiredArgsConstructor;
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
    private final ResourceTypeMapper resourceTypeMapper;
    private final AuditLogService auditLogService;

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
    public ResourceTypeDTO createResourceType(ResourceTypeDTO resourceTypeDTO) {
        ResourceType resourceType = resourceTypeMapper.toEntity(resourceTypeDTO);
        ResourceType savedType = resourceTypeRepository.save(resourceType);
        
        // Log the action
        auditLogService.logAdminAction("ResourceType", "create", 
                "Created resource type: " + savedType.getName());
                
        return resourceTypeMapper.toDto(savedType);
    }

    /**
     * Update existing resource type
     */
    @Transactional
    public Optional<ResourceTypeDTO> updateResourceType(Long id, ResourceTypeDTO resourceTypeDTO) {
        return resourceTypeRepository.findById(id)
                .map(existingType -> {
                    // Apply updates from DTO to entity
                    existingType.setName(resourceTypeDTO.getName());
                    existingType.setColor(resourceTypeDTO.getColor());
                    
                    // Save updated entity
                    ResourceType updatedType = resourceTypeRepository.save(existingType);
                    
                    // Log the action
                    auditLogService.logAdminAction("ResourceType", "update", 
                            "Updated resource type: " + updatedType.getName());
                    
                    return resourceTypeMapper.toDto(updatedType);
                });
    }

    /**
     * Delete resource type
     * Returns false if the type doesn't exist or is in use
     */
    @Transactional
    public boolean deleteResourceType(Long id) {
        // Check if the resource type exists
        if (!resourceTypeRepository.existsById(id)) {
            return false;
        }
        
        // Check if the resource type is in use
        if (!resourceRepository.findByTypeId(id).isEmpty()) {
            return false;
        }
        
        // Get type name for logging before deletion
        String typeName = resourceTypeRepository.findById(id)
                .map(ResourceType::getName)
                .orElse("Unknown");
        
        // Delete the resource type
        resourceTypeRepository.deleteById(id);
        
        // Log the action
        auditLogService.logAdminAction("ResourceType", "delete", 
                "Deleted resource type: " + typeName);
        
        return true;
    }

    /**
     * Check if a resource type exists by name
     */
    public boolean existsByName(String name) {
        return resourceTypeRepository.existsByName(name);
    }
}