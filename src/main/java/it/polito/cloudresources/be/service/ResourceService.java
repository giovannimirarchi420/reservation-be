package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.ResourceDTO;
import it.polito.cloudresources.be.mapper.ResourceMapper;
import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.model.User;
import it.polito.cloudresources.be.repository.EventRepository;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.util.DateTimeUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for resource operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final EventRepository eventRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ResourceMapper resourceMapper;
    private final DateTimeUtils dateTimeUtils;

    /**
     * Get all resources
     */
    public List<ResourceDTO> getAllResources() {
        return resourceMapper.toDto(resourceRepository.findAll());
    }

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
     * Create new resource
     */
    @Transactional
    public ResourceDTO createResource(ResourceDTO resourceDTO) {
        Resource resource = resourceMapper.toEntity(resourceDTO);
        Resource savedResource = resourceRepository.save(resource);
        
        // Log the action
        auditLogService.logAdminAction("Resource", "create", "Created resource: " + savedResource.getName());
        
        // Notify admins about new resource
        notificationService.createSystemNotification(
            "New resource created", 
            "Resource " + savedResource.getName() + " has been added to the system"
        );
        
        return resourceMapper.toDto(savedResource);
    }

    /**
     * Update existing resource
     */
    @Transactional
    public Optional<ResourceDTO> updateResource(Long id, ResourceDTO resourceDTO) {
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
    public Optional<ResourceDTO> updateResourceStatus(Long id, ResourceStatus status) {
        return resourceRepository.findById(id)
                .map(existingResource -> {
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
    public boolean deleteResource(Long id) {
        Optional<Resource> resourceOpt = resourceRepository.findById(id);
        if (resourceOpt.isPresent()) {
            Resource resource = resourceOpt.get();
            
            // Check if resource has future bookings
            ZonedDateTime now = dateTimeUtils.getCurrentDateTime();
            List<Event> futureEvents = eventRepository.findByResourceId(id).stream()
                    .filter(event -> event.getEnd().isAfter(now))
                    .collect(Collectors.toList());
            
            // Notify users with future bookings
            if (!futureEvents.isEmpty()) {
                Set<User> usersToNotify = new HashSet<>();
                
                // Collect all unique users with future bookings
                // Delete all future bookings
                for (Event event : futureEvents) {
                    usersToNotify.add(event.getUser());
                    eventRepository.delete(event);
                }

                // Send notification to each affected user
                for (User user : usersToNotify) {
                    notificationService.createNotification(
                        user.getId(),
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
    public List<ResourceDTO> searchResources(String query) {
        return resourceMapper.toDto(
                resourceRepository.findByNameContainingOrSpecsContainingOrLocationContaining(
                        query, query, query)
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
                Set<User> usersToNotify = new HashSet<>();
                
                // Collect all unique users with future bookings
                for (Event event : futureEvents) {
                    usersToNotify.add(event.getUser());
                }
                
                // Send notification to each affected user
                for (User user : usersToNotify) {
                    notificationService.createNotification(
                        user.getId(),
                        "Resource unavailable: " + resource.getName() + " is now " + newStatus.toString().toLowerCase(),
                        "WARNING"
                    );
                }
                
                // Log the notifications
                log.info("Sent notifications to {} users about resource status change for {}", 
                        usersToNotify.size(), resource.getName());
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
            Set<User> usersToNotify = new HashSet<>();
            
            // Collect all unique users with future bookings
            for (Event event : futureEvents) {
                usersToNotify.add(event.getUser());
            }
            
            // Send notification to each affected user
            for (User user : usersToNotify) {
                notificationService.createNotification(
                    user.getId(),
                    "Resource available: " + resource.getName() + " is now active",
                    "SUCCESS"
                );
            }
            
            if (!usersToNotify.isEmpty()) {
                // Log the notifications
                log.info("Sent notifications to {} users about resource becoming active again: {}", 
                        usersToNotify.size(), resource.getName());
            }
        }
    }

    public void collectAllSubResources(Resource resource, List<Resource> subResources) {
        resource.getSubResources().forEach(subResource -> {
            subResources.add(subResource);
            collectAllSubResources(subResource, subResources);
        });
    }

}