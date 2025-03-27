package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.ResourceDTO;
import it.polito.cloudresources.be.mapper.ResourceMapper;
import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.repository.EventRepository;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for resource operations
 * Updated to work with Keycloak user IDs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final EventRepository eventRepository;
    private final NotificationService notificationService;
    private final KeycloakService keycloakService;
    private final AuditLogService auditLogService;
    private final ResourceMapper resourceMapper;
    private final DateTimeUtils dateTimeUtils;

    public List<ResourceDTO> getAllResources(String userId) {
        if (keycloakService.hasGlobalAdminRole(userId)) {
            // Global admins see all resources
            return resourceMapper.toDto(resourceRepository.findAll());
        } else {
            // Federation admins and regular users see only resources in their federations
            List<String> userFederations = keycloakService.getUserFederations(userId);
            return resourceMapper.toDto(resourceRepository.findByFederationIdIn(userFederations));
        }
    }

    public List<ResourceDTO> getResourcesByFederation(String federationId) {
        return resourceMapper.toDto(resourceRepository.findByFederationId(federationId));
    }
    
    @Transactional
    public ResourceDTO createResource(ResourceDTO resourceDTO, String userId) {
        // Check authorization
        if (!canUpdateResourceInFederation(userId, resourceDTO.getFederationId())) {
            throw new AccessDeniedException("You don't have permission to create resources in this federation");
        }
        
        // Create resource
        Resource resource = resourceMapper.toEntity(resourceDTO);
        Resource savedResource = resourceRepository.save(resource);
        
        // Log the action
        auditLogService.logAdminAction("Resource", "create", 
                "Created resource: " + savedResource.getName() + " in federation: " + resourceDTO.getFederationName());
        
        return resourceMapper.toDto(savedResource);
    }
    
    // Similar changes for update, delete, etc.

    /**
     * Get resource by ID
     */
    public Optional<ResourceDTO> getResourceById(Long id) {
        return resourceRepository.findById(id)
                .map(resourceMapper::toDto);
    }

    /**
     * Get resources by status
     */
    public List<ResourceDTO> getResourcesByStatus(ResourceStatus status) {
        return resourceMapper.toDto(resourceRepository.findByStatus(status));
    }

    /**
     * Get resources by type
     */
    public List<ResourceDTO> getResourcesByType(Long typeId) {
        return resourceMapper.toDto(resourceRepository.findByTypeId(typeId));
    }

    /**
     * Update existing resource
     */
    @Transactional
    public Optional<ResourceDTO> updateResource(Long id, ResourceDTO resourceDTO, String userId) {
        // Check authorization
        if (!canUpdateResourceInFederation(userId, resourceDTO.getFederationId())) {
            throw new AccessDeniedException("You don't have permission to update resources in this federation");
        }

        return resourceRepository.findById(id)
                .map(existingResource -> {
                    // Store old status for comparison
                    ResourceStatus oldStatus = existingResource.getStatus();
                    
                    // Update resource using mapper
                    Resource updatedResource = resourceMapper.toEntity(resourceDTO);
                    updatedResource.setId(id);
                    
                    // Save the updated resource
                    Resource savedResource = resourceRepository.save(updatedResource);
                    
                    // Log the action
                    auditLogService.logAdminAction("Resource", "update", 
                            "Updated resource: " + savedResource.getName());
                    
                    // Check if status has changed
                    if (oldStatus != savedResource.getStatus()) {
                        // Handle status change notifications
                        handleResourceStatusChange(savedResource, oldStatus);
                    }
                    
                    return resourceMapper.toDto(savedResource);
                });
    }

    /**
     * Update resource status
     */
    @Transactional
    public Optional<ResourceDTO> updateResourceStatus(Long id, ResourceStatus status, String userId) {
        return resourceRepository.findById(id)
                .map(existingResource -> {
        
                    // Check authorization
                    if (!canUpdateResourceInFederation(userId, existingResource.getFederationId())) {
                        throw new AccessDeniedException("You don't have permission to update resources in this federation");
                    }
        
                    ResourceStatus oldStatus = existingResource.getStatus();
                    existingResource.setStatus(status);
                    Resource updatedResource = resourceRepository.save(existingResource);
                    
                    // Log the action
                    auditLogService.logAdminAction("Resource", "updateStatus", 
                            "Updated resource status: " + updatedResource.getName() + 
                            " from " + oldStatus + " to " + status);
                    
                    // Handle status change notifications
                    handleResourceStatusChange(updatedResource, oldStatus);
                    
                    return resourceMapper.toDto(updatedResource);
                });
    }

    /**
     * Delete resource
     */
    @Transactional
    public boolean deleteResource(Long id, String userId) {
        Optional<Resource> resourceOpt = resourceRepository.findById(id);
        if (resourceOpt.isPresent()) {
            Resource resource = resourceOpt.get();
            // Check authorization
            if (!canUpdateResourceInFederation(userId, resource.getFederationId())) {
                throw new AccessDeniedException("You don't have permission to update resources in this federation");
            }
            // Check if resource has future bookings
            ZonedDateTime now = dateTimeUtils.getCurrentDateTime();
            List<Event> futureEvents = eventRepository.findByResourceId(id).stream()
                    .filter(event -> event.getEnd().isAfter(now))
                    .collect(Collectors.toList());
            
            // Notify users with future bookings
            if (!futureEvents.isEmpty()) {
                Set<String> keycloakIdsToNotify = new HashSet<>();
                
                // Collect all unique users with future bookings
                // Delete all future bookings
                for (Event event : futureEvents) {
                    keycloakIdsToNotify.add(event.getKeycloakId());
                    eventRepository.delete(event);
                }

                // Send notification to each affected user
                for (String keycloakId : keycloakIdsToNotify) {
                    notificationService.createNotification(
                        keycloakId,
                        "Booking cancelled: Resource " + resource.getName() + " has been removed",
                        "ERROR"
                    );
                }
            }
            
            // Log the action
            auditLogService.logAdminAction("Resource", "delete", "Deleted resource: " + resource.getName());
            
            // Delete the resource
            resourceRepository.deleteById(id);
            
            // Notify admins about deletion
            notificationService.createSystemNotification(
                "Resource deleted", 
                "Resource " + resource.getName() + " has been deleted from the system"
            );
            
            return true;
        }
        return false;
    }

    /**
     * Search resources
     */
    public List<ResourceDTO> searchResources(String query, List<String> federationIds) {
        return resourceMapper.toDto(
                resourceRepository.findByFederationIdInAndNameContainingOrSpecsContainingOrLocationContaining(
                    federationIds, query, query, query)
        );
    }

    /**
     * Handle resource status change notifications
     */
    private void handleResourceStatusChange(Resource resource, ResourceStatus oldStatus) {
        ResourceStatus newStatus = resource.getStatus();
        
        // Skip if status didn't actually change
        if (oldStatus == newStatus) {
            return;
        }
        
        // Notify admins about the status change
        notificationService.createSystemNotification(
            "Resource status changed", 
            "Resource " + resource.getName() + " status changed from " + oldStatus + " to " + newStatus
        );
        
        // If resource is no longer active, notify users with upcoming bookings
        if (oldStatus == ResourceStatus.ACTIVE && 
            (newStatus == ResourceStatus.MAINTENANCE || newStatus == ResourceStatus.UNAVAILABLE)) {
            
            // Find all future bookings for this resource
            ZonedDateTime now = dateTimeUtils.getCurrentDateTime();
            List<Event> futureEvents = eventRepository.findByResourceId(resource.getId()).stream()
                    .filter(event -> event.getEnd().isAfter(now))
                    .collect(Collectors.toList());
            
            if (!futureEvents.isEmpty()) {
                // Create a set to avoid duplicate notifications to the same user
                Set<String> keycloakIdsToNotify = new HashSet<>();
                
                // Collect all unique users with future bookings
                for (Event event : futureEvents) {
                    keycloakIdsToNotify.add(event.getKeycloakId());
                }
                
                // Send notification to each affected user
                for (String keycloakId : keycloakIdsToNotify) {
                    notificationService.createNotification(
                        keycloakId,
                        "Resource unavailable: " + resource.getName() + " is now " + newStatus.toString().toLowerCase(),
                        "WARNING"
                    );
                }
                
                // Log the notifications
                log.info("Sent notifications to {} users about resource status change for {}", 
                        keycloakIdsToNotify.size(), resource.getName());
            }
        }
        
        // If resource becomes active again, notify users who had bookings during maintenance
        if ((oldStatus == ResourceStatus.MAINTENANCE || oldStatus == ResourceStatus.UNAVAILABLE) && 
             newStatus == ResourceStatus.ACTIVE) {
            
            // Find all users with upcoming bookings for this resource
            ZonedDateTime now = dateTimeUtils.getCurrentDateTime();
            List<Event> futureEvents = eventRepository.findByResourceId(resource.getId()).stream()
                    .filter(event -> event.getEnd().isAfter(now))
                    .collect(Collectors.toList());
            
            // Create a set to avoid duplicate notifications to the same user
            Set<String> keycloakIdsToNotify = new HashSet<>();
            
            // Collect all unique users with future bookings
            for (Event event : futureEvents) {
                keycloakIdsToNotify.add(event.getKeycloakId());
            }
            
            // Send notification to each affected user
            for (String keycloakId : keycloakIdsToNotify) {
                notificationService.createNotification(
                    keycloakId,
                    "Resource available: " + resource.getName() + " is now active",
                    "SUCCESS"
                );
            }
            
            if (!keycloakIdsToNotify.isEmpty()) {
                // Log the notifications
                log.info("Sent notifications to {} users about resource becoming active again: {}", 
                        keycloakIdsToNotify.size(), resource.getName());
            }
        }
    }

    public void collectAllSubResources(Resource resource, List<Resource> subResources) {
        resource.getSubResources().forEach(subResource -> {
            subResources.add(subResource);
            collectAllSubResources(subResource, subResources);
        });
    }

    private boolean canUpdateResourceInFederation(String userId, String federationId) {
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