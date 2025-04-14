package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.ResourceDTO;
import it.polito.cloudresources.be.mapper.ResourceMapper;
import it.polito.cloudresources.be.model.AuditLog;
import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.model.WebhookConfig;
import it.polito.cloudresources.be.model.WebhookEventType;
import it.polito.cloudresources.be.model.WebhookLog;
import it.polito.cloudresources.be.repository.EventRepository;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.WebhookConfigRepository;
import it.polito.cloudresources.be.repository.WebhookLogRepository;
import it.polito.cloudresources.be.util.DateTimeUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.access.AccessDeniedException;
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
    private final WebhookService webhookService;
    private final DateTimeUtils dateTimeUtils;
    private final WebhookLogRepository webhookLogRepository;
    private final WebhookConfigRepository webhookConfigRepository;


    public List<ResourceDTO> getAllResources(String userId) {
        if (keycloakService.hasGlobalAdminRole(userId)) {
            // Global admins see all resources
            return resourceMapper.toDto(resourceRepository.findAll());
        } else {
            // Site admins and regular users see only resources in their site
            List<String> userSites = keycloakService.getUserSites(userId);
            System.out.print("TEST TEST: " + userSites);
            log.info("User requested with sites: " + userSites);
            return resourceMapper.toDto(resourceRepository.findBySiteIdIn(userSites));
        }
    }

    public List<ResourceDTO> getResourcesBySite(String siteId, String currentUserKeycloakId) {
        // Check if user has access to this site
        if (!keycloakService.isUserInGroup(currentUserKeycloakId, siteId) &&
            !keycloakService.hasGlobalAdminRole(currentUserKeycloakId)) {
            throw new AccessDeniedException("User don't have permission to access resources in this site");
        }

        return resourceMapper.toDto(resourceRepository.findBySiteId(siteId));
    }

    public ResourceDTO createResource(ResourceDTO resourceDTO, String userId) {
        // Check authorization
        if (!canUpdateResourceInSite(userId, resourceDTO.getSiteId())) {
            throw new AccessDeniedException("You don't have permission to create resources in this site");
        }
        
        // Create resource
        Resource resource = resourceMapper.toEntity(resourceDTO);
        Resource savedResource = resourceRepository.save(resource);

        auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                AuditLog.LogAction.CREATE,
                new AuditLog.LogEntity("RESOURCE", savedResource.getId().toString()),
                "Admin " + userId + "created resource " + savedResource.getName() + " in site " + savedResource.getSiteId() + "(id: " + savedResource.getId());

        webhookService.processResourceEvent(WebhookEventType.RESOURCE_CREATED, savedResource, savedResource);

        return resourceMapper.toDto(savedResource);
    }

    /**
     * Get resource by ID
     */
    public ResourceDTO getResourceById(Long id, String userId) {
        Optional<Resource> resource = resourceRepository.findById(id);

        if(!resource.isPresent()) {
            throw new EntityNotFoundException("Resource not found");
        }

        String siteId = resource.get().getSiteId();

        if (!keycloakService.getUserSites(userId).contains(siteId)) {
            throw new AccessDeniedException("Resource can't be accessed by user");
        }

        return resourceMapper.toDto(resource.get());
    }

    /**
     * Get resources by status
     */
    public List<ResourceDTO> getResourcesByStatus(ResourceStatus status, String userId) {
        List<String> siteIds = keycloakService.getUserSites(userId);
        return resourceMapper.toDto(resourceRepository.findBySiteIdInAndStatus(siteIds, status));
    }

    /**
     * Get resources by type
     */
    public List<ResourceDTO> getResourcesByType(Long typeId, String userId) {
        List<String> siteIds = keycloakService.getUserSites(userId);
        return resourceMapper.toDto(resourceRepository.findBySiteIdInAndTypeId(siteIds, typeId));
    }

    /**
     * Update existing resource
     */
    @Transactional
    public Optional<ResourceDTO> updateResource(Long id, ResourceDTO resourceDTO, String userId) {
        // Check authorization
        if (!canUpdateResourceInSite(userId, resourceDTO.getSiteId())) {
            throw new AccessDeniedException("You don't have permission to update resources in this site");
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

                    auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                            AuditLog.LogAction.UPDATE,
                            new AuditLog.LogEntity("RESOURCE", savedResource.getId().toString()),
                            "Admin "+ userId + " updated resource to: " + updatedResource);
                    
                    webhookService.processResourceEvent(WebhookEventType.RESOURCE_UPDATED, savedResource, savedResource);
                    // Check if status has changed
                    if (oldStatus != savedResource.getStatus()) {
                        // Handle status change notifications
                        handleResourceStatusChange(savedResource, oldStatus);
                        webhookService.processResourceEvent(WebhookEventType.RESOURCE_STATUS_CHANGED, savedResource, savedResource);
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
                    if (!canUpdateResourceInSite(userId, existingResource.getSiteId())) {
                        throw new AccessDeniedException("You don't have permission to update resources in this site");
                    }
        
                    ResourceStatus oldStatus = existingResource.getStatus();
                    existingResource.setStatus(status);
                    Resource updatedResource = resourceRepository.save(existingResource);

                    auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                            AuditLog.LogAction.UPDATE,
                            new AuditLog.LogEntity("RESOURCE", updatedResource.getId().toString()),
                            "Admin "+ userId + "updated resource status to: "+ status.toString());
                    
                    // Handle status change notifications
                    handleResourceStatusChange(updatedResource, oldStatus);
                    
                    return resourceMapper.toDto(updatedResource);
                });
    }

    /**
     * Delete resource
     */
    @Transactional
    public void deleteResource(Long id, String userId) {
        Optional<Resource> resourceOpt = resourceRepository.findById(id);

        if(!resourceOpt.isPresent()) {
            throw new EntityNotFoundException("Resource not found");
        }

        Resource resource = resourceOpt.get();

        if (!canUpdateResourceInSite(userId, resource.getSiteId())) {
            throw new AccessDeniedException("You don't have permission to delete resources in this site");
        }

        ZonedDateTime now = dateTimeUtils.getCurrentDateTime();

        // 1. First handle all events related to this resource
        List<Event> allEvents = eventRepository.findByResourceId(id);
        
        // Notify users with future bookings
        if (!allEvents.isEmpty()) {
            Set<String> keycloakIdsToNotify = new HashSet<>();

            for (Event event : allEvents) {
                // Only notify for future events
                if (event.getEnd().isAfter(now)) {
                    keycloakIdsToNotify.add(event.getKeycloakId());
                }
                // Delete the event
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

        
        // 2. Get all sub-resources to delete them first (recursive deletion)
        List<Resource> subResources = new ArrayList<>(resourceRepository.findByParentId(id));
        
        // Delete all sub-resources first
        for (Resource subResource : subResources) {
            try {
                // Recursive call to delete sub-resources
                deleteResource(subResource.getId(), userId);
            } catch (Exception e) {
                log.error("Error deleting sub-resource {}: {}", subResource.getId(), e.getMessage());
            }
        }
        
        // 3. Handle webhook logs related to this resource
        try {
            List<WebhookLog> webhookLogs = webhookLogRepository.findByResourceId(id);
            if (!webhookLogs.isEmpty()) {
                log.info("Deleting {} webhook logs for resource {}", webhookLogs.size(), id);
                webhookLogRepository.deleteAll(webhookLogs);
            }
        } catch (Exception e) {
            log.error("Error deleting webhook logs for resource {}: {}", id, e.getMessage());
        }
        
        // 4. Handle webhook configs that reference this resource
        try {
            List<WebhookConfig> webhookConfigs = webhookConfigRepository.findByResourceId(id);
            if (!webhookConfigs.isEmpty()) {
                log.info("Updating {} webhook configs for resource {}", webhookConfigs.size(), id);
                
                // Instead of deleting, set the resource to null (if the webhook is for this resource specifically)
                for (WebhookConfig webhookConfig : webhookConfigs) {
                    webhookConfig.setResource(null);
                    webhookConfigRepository.save(webhookConfig);
                }
            }
        } catch (Exception e) {
            log.error("Error updating webhook configs for resource {}: {}", id, e.getMessage());
        }
        
        // 5. Finally delete the resource itself
        try {
            resourceRepository.delete(resource);
            
            auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                    AuditLog.LogAction.DELETE,
                    new AuditLog.LogEntity("RESOURCE", id.toString()),
                    "Admin " + userId + " deleted resource: " + resource);
            
            // Notify admins about deletion
            notificationService.createSystemNotification(
                    "Resource deleted",
                    "Resource " + resource.getName() + " has been deleted from the system"
            );
            
            // Process webhook event for resource deletion
            webhookService.processResourceEvent(WebhookEventType.RESOURCE_DELETED, resource, resource);
            
        } catch (Exception e) {
            log.error("Error deleting resource {}: {}", id, e.getMessage());
            throw new RuntimeException("Failed to delete resource: " + e.getMessage(), e);
        }
    }
    /**
     * Search resources
     */
    public List<ResourceDTO> searchResources(String query, String userId) {
        List<String> siteIds = keycloakService.getUserSites(userId);

        return resourceMapper.toDto(
                resourceRepository.findBySiteIdInAndNameContainingOrSpecsContainingOrLocationContaining(
                    siteIds, query, query, query)
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
                    .filter(event -> event.getEnd().isAfter(now)).toList();
            
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

    
    /**
     * Check if user can access a resource (is in the resource's site)
     */
    public boolean canAccessResource(String userId, Resource resource) {
        // Global admins can access all resources
        if (keycloakService.hasGlobalAdminRole(userId)) {
            return true;
        }
        
        // Check if user is in the resource's site
        return keycloakService.isUserInGroup(userId, resource.getSiteId());
    }

    private boolean canUpdateResourceInSite(String userId, String siteId) {
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