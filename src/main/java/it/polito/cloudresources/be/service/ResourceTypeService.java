package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.ResourceTypeDTO;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for resource type operations
 */
@Service
@RequiredArgsConstructor
public class ResourceTypeService {

    private final ResourceTypeRepository resourceTypeRepository;
    private final ResourceRepository resourceRepository;
    private final ModelMapper modelMapper;

    /**
     * Get all resource types
     */
    public List<ResourceTypeDTO> getAllResourceTypes() {
        return resourceTypeRepository.findAll().stream()
                .map(type -> modelMapper.map(type, ResourceTypeDTO.class))
                .collect(Collectors.toList());
    }

    /**
     * Get resource type by ID
     */
    public Optional<ResourceTypeDTO> getResourceTypeById(Long id) {
        return resourceTypeRepository.findById(id)
                .map(type -> modelMapper.map(type, ResourceTypeDTO.class));
    }

    /**
     * Create new resource type
     */
    @Transactional
    public ResourceTypeDTO createResourceType(ResourceTypeDTO resourceTypeDTO) {
        ResourceType resourceType = modelMapper.map(resourceTypeDTO, ResourceType.class);
        ResourceType savedType = resourceTypeRepository.save(resourceType);
        return modelMapper.map(savedType, ResourceTypeDTO.class);
    }

    /**
     * Update existing resource type
     */
    @Transactional
    public Optional<ResourceTypeDTO> updateResourceType(Long id, ResourceTypeDTO resourceTypeDTO) {
        return resourceTypeRepository.findById(id)
                .map(existingType -> {
                    existingType.setName(resourceTypeDTO.getName());
                    existingType.setColor(resourceTypeDTO.getColor());
                    return modelMapper.map(resourceTypeRepository.save(existingType), ResourceTypeDTO.class);
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
        
        // Delete the resource type
        resourceTypeRepository.deleteById(id);
        return true;
    }

    /**
     * Check if a resource type exists by name
     */
    public boolean existsByName(String name) {
        return resourceTypeRepository.existsByName(name);
    }
}
