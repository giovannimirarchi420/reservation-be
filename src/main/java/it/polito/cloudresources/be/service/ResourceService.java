package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.ResourceDTO;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for resource operations
 */
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final ModelMapper modelMapper;

    /**
     * Get all resources
     */
    public List<ResourceDTO> getAllResources() {
        return resourceRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get resource by ID
     */
    public Optional<ResourceDTO> getResourceById(Long id) {
        return resourceRepository.findById(id)
                .map(this::convertToDTO);
    }

    /**
     * Get resources by status
     */
    public List<ResourceDTO> getResourcesByStatus(ResourceStatus status) {
        return resourceRepository.findByStatus(status).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get resources by type
     */
    public List<ResourceDTO> getResourcesByType(Long typeId) {
        return resourceRepository.findByTypeId(typeId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create new resource
     */
    @Transactional
    public ResourceDTO createResource(ResourceDTO resourceDTO) {
        Resource resource = convertToEntity(resourceDTO);
        Resource savedResource = resourceRepository.save(resource);
        return convertToDTO(savedResource);
    }

    /**
     * Update existing resource
     */
    @Transactional
    public Optional<ResourceDTO> updateResource(Long id, ResourceDTO resourceDTO) {
        return resourceRepository.findById(id)
                .map(existingResource -> {
                    // Update fields
                    existingResource.setName(resourceDTO.getName());
                    existingResource.setSpecs(resourceDTO.getSpecs());
                    existingResource.setLocation(resourceDTO.getLocation());
                    existingResource.setStatus(resourceDTO.getStatus());
                    
                    // Update resource type if changed
                    if (!existingResource.getType().getId().equals(resourceDTO.getTypeId())) {
                        ResourceType newType = resourceTypeRepository.findById(resourceDTO.getTypeId())
                                .orElseThrow(() -> new EntityNotFoundException("Resource type not found"));
                        existingResource.setType(newType);
                    }
                    
                    return convertToDTO(resourceRepository.save(existingResource));
                });
    }

    /**
     * Update resource status
     */
    @Transactional
    public Optional<ResourceDTO> updateResourceStatus(Long id, ResourceStatus status) {
        return resourceRepository.findById(id)
                .map(existingResource -> {
                    existingResource.setStatus(status);
                    return convertToDTO(resourceRepository.save(existingResource));
                });
    }

    /**
     * Delete resource
     */
    @Transactional
    public boolean deleteResource(Long id) {
        if (resourceRepository.existsById(id)) {
            resourceRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Search resources
     */
    public List<ResourceDTO> searchResources(String query) {
        return resourceRepository.findByNameContainingOrSpecsContainingOrLocationContaining(
                query, query, query).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Convert entity to DTO
     */
    private ResourceDTO convertToDTO(Resource resource) {
        ResourceDTO dto = modelMapper.map(resource, ResourceDTO.class);
        dto.setTypeId(resource.getType().getId());
        dto.setTypeName(resource.getType().getName());
        dto.setTypeColor(resource.getType().getColor());
        return dto;
    }

    /**
     * Convert DTO to entity
     */
    private Resource convertToEntity(ResourceDTO dto) {
        Resource resource = modelMapper.map(dto, Resource.class);
        
        // Set the resource type
        ResourceType type = resourceTypeRepository.findById(dto.getTypeId())
                .orElseThrow(() -> new EntityNotFoundException("Resource type not found"));
        resource.setType(type);
        
        return resource;
    }
}
