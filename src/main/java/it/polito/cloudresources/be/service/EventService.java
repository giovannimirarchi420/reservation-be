package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.EventDTO;
import it.polito.cloudresources.be.mapper.EventMapper;
import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.repository.EventRepository;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.util.DateTimeUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for event operations with consistent time zone handling
 * Updated to support federation access control
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final ResourceRepository resourceRepository;
    private final NotificationService notificationService;
    private final ResourceService resourceService;
    private final KeycloakService keycloakService;
    private final EventMapper eventMapper;
    private final DateTimeUtils dateTimeUtils;

    /**
     * Get all events based on user federation access
     */
    public List<EventDTO> getAllEvents(String userId) {
        if (keycloakService.hasGlobalAdminRole(userId)) {
            // Global admins see all events
            return eventMapper.toDto(eventRepository.findAll());
        } else {
            // Federation admins and regular users see only events for resources in their federations
            List<String> userFederations = keycloakService.getUserFederations(userId);
            
            // Get all resources from user's federations
            List<Resource> federationResources = resourceRepository.findByFederationIdIn(userFederations);
            
            if (federationResources.isEmpty()) {
                return new ArrayList<>();
            }
            
            // Extract resource IDs
            List<Long> resourceIds = federationResources.stream()
                .map(Resource::getId)
                .collect(Collectors.toList());
                
            // Get events for these resources
            List<Event> events = eventRepository.findByResourceIdIn(resourceIds);
            return eventMapper.toDto(events);
        }
    }

    public List<EventDTO> getEventsByFederation(String federationId, String userId) {
        // Validate user has access
        if (!keycloakService.isUserInFederation(userId, federationId) && 
            !keycloakService.hasGlobalAdminRole(userId)) {
            throw new AccessDeniedException("User does not have access to this federation");
        }
        
        // Get resources in this federation
        List<Resource> federationResources = resourceRepository.findByFederationId(federationId);
        
        if (federationResources.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Get events for these resources
        List<Long> resourceIds = federationResources.stream()
            .map(Resource::getId)
            .collect(Collectors.toList());
            
        List<Event> events = eventRepository.findByResourceIdIn(resourceIds);
        return eventMapper.toDto(events);
    }

    /**
     * Get event by ID
     */
    public Optional<EventDTO> getEventById(Long id, String userId) {
        Optional<Event> eventOpt = eventRepository.findById(id);
        
        if (!eventOpt.isPresent()) {
            return Optional.empty();
        }
        
        Event event = eventOpt.get();
        
        // Check if the user can access this event
        if (!canAccessEvent(userId, event)) {
            return Optional.empty();
        }
        
        return Optional.of(eventMapper.toDto(event));
    }

    /**
     * Get events by resource
     */
    public List<EventDTO> getEventsByResource(Long resourceId, String userId) {
        // Check if the user has access to this resource
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new EntityNotFoundException("Resource not found with ID: " + resourceId));
        
        if (!canAccessResource(userId, resource)) {
            throw new AccessDeniedException("You don't have access to events for this resource");
        }
        
        return eventMapper.toDto(eventRepository.findByResourceId(resourceId));
    }

    /**
     * Get events by user's Keycloak ID
     */
    public List<EventDTO> getEventsByUserKeycloakId(String keycloakId, String requestUserId) {
        // Users can always see their own events, administrators see events from their federation
        if (keycloakId.equals(requestUserId) || keycloakService.hasGlobalAdminRole(requestUserId)) {
            return eventMapper.toDto(eventRepository.findByKeycloakId(keycloakId));
        }
        
        // Federation admins can see events from users in their federations
        List<String> adminFederations = keycloakService.getUserAdminFederations(requestUserId);
        List<String> userFederations = keycloakService.getUserFederations(keycloakId);
        
        // Check if the request user is admin of any federation the target user belongs to
        boolean hasAdminAccess = adminFederations.stream()
                .anyMatch(userFederations::contains);
        
        if (!hasAdminAccess) {
            throw new AccessDeniedException("You don't have access to this user's events");
        }
        
        // Get the user's events but filter to only federations the admin has access to
        List<String> commonFederations = adminFederations.stream()
                .filter(userFederations::contains)
                .collect(Collectors.toList());
                
        List<Resource> accessibleResources = resourceRepository.findByFederationIdIn(commonFederations);
        
        if (accessibleResources.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Long> resourceIds = accessibleResources.stream()
                .map(Resource::getId)
                .collect(Collectors.toList());
                
        // Get all events for this user
        List<Event> userEvents = eventRepository.findByKeycloakId(keycloakId);
        
        // Filter to only events for resources in accessible federations
        List<Event> accessibleEvents = userEvents.stream()
                .filter(event -> resourceIds.contains(event.getResource().getId()))
                .collect(Collectors.toList());
                
        return eventMapper.toDto(accessibleEvents);
    }

    /**
     * Get events by date range
     */
    public List<EventDTO> getEventsByDateRange(ZonedDateTime startDate, ZonedDateTime endDate, String userId) {
        // Make sure both dates have time zone info
        ZonedDateTime normalizedStartDate = dateTimeUtils.ensureTimeZone(startDate);
        ZonedDateTime normalizedEndDate = dateTimeUtils.ensureTimeZone(endDate);
        
        List<Event> events = eventRepository.findByDateRange(normalizedStartDate, normalizedEndDate);
        
        // Filter events based on federation access
        List<Event> accessibleEvents;
        
        if (keycloakService.hasGlobalAdminRole(userId)) {
            // Global admins see all events
            accessibleEvents = events;
        } else {
            // Federation users see only events for resources in their federations
            List<String> userFederations = keycloakService.getUserFederations(userId);
            
            accessibleEvents = events.stream()
                    .filter(event -> userFederations.contains(event.getResource().getFederationId()))
                    .collect(Collectors.toList());
        }
        
        return eventMapper.toDto(accessibleEvents);
    }

    /**
     * Create new event
     */
    @Transactional
    public EventDTO createEvent(EventDTO eventDTO, String userId) {
        log.debug("Creating event with DTO: {}", eventDTO);
        
        // Set current time if not provided by frontend
        if (eventDTO.getStart() == null) {
            eventDTO.setStart(dateTimeUtils.getCurrentDateTime());
        } else {
            // Ensure time zone info for start
            eventDTO.setStart(dateTimeUtils.ensureTimeZone(eventDTO.getStart()));
        }
        
        if (eventDTO.getEnd() == null) {
            // Default to 1 hour later if end time not provided
            eventDTO.setEnd(dateTimeUtils.getCurrentDateTime().plusHours(1));
        } else {
            // Ensure time zone info for end
            eventDTO.setEnd(dateTimeUtils.ensureTimeZone(eventDTO.getEnd()));
        }
        
        // Validate time period
        if (eventDTO.getEnd().isBefore(eventDTO.getStart())) {
            throw new IllegalStateException("End time must be after start time");
        }
        
        // Get the resource and check if user has access
        Resource resource = resourceRepository.findById(eventDTO.getResourceId())
                .orElseThrow(() -> new EntityNotFoundException("Resource not found with ID: " + eventDTO.getResourceId()));
        
        // Check if user has access to this resource's federation
        if (!canAccessResource(userId, resource)) {
            throw new AccessDeniedException("You don't have access to book this resource");
        }
        
        // Check for time conflicts
        if (hasTimeConflict(eventDTO.getResourceId(), eventDTO.getStart(), eventDTO.getEnd(), null)) {
            throw new IllegalStateException("The selected time period conflicts with existing bookings");
        }
        
        // Check if the resource is in ACTIVE state
        if (resource.getStatus() != ResourceStatus.ACTIVE) {
            throw new IllegalStateException("Cannot book a resource that is not in ACTIVE state. Current state: " + resource.getStatus());
        }
        
        // If userId is different from event's userId, check if requester is admin
        String eventUserId = eventDTO.getUserId() != null ? eventDTO.getUserId() : userId;
        
        if (!eventUserId.equals(userId)) {
            // Check if the requester is admin for the resource's federation
            if (!keycloakService.hasGlobalAdminRole(userId) && 
                !keycloakService.isUserFederationAdmin(userId, resource.getFederationId())) {
                throw new AccessDeniedException("Only administrators can create bookings for other users");
            }
            
            // Check if the target user is in the federation
            if (!keycloakService.isUserInFederation(eventUserId, resource.getFederationId())) {
                throw new IllegalStateException("The user must be a member of the resource's federation to book it");
            }
        }
        
        // Set the user ID to the actual user if not specified
        if (eventDTO.getUserId() == null) {
            eventDTO.setUserId(userId);
        }
        
        // Verify user exists in Keycloak
        keycloakService.getUserById(eventDTO.getUserId())
            .orElseThrow(() -> new EntityNotFoundException("User not found with Keycloak ID: " + eventDTO.getUserId()));
        
        Event event = eventMapper.toEntity(eventDTO);
        Event savedEvent = eventRepository.save(event);
        
        log.debug("Saved event: {}", savedEvent);
        
        // Get user display name for notification
        String userDisplayName = "Unknown user";
        try {
            Optional<UserRepresentation> user = keycloakService.getUserById(event.getKeycloakId());
            if (user.isPresent()) {
                userDisplayName = user.get().getFirstName() + " " + user.get().getLastName();
            }
        } catch (Exception e) {
            log.warn("Could not fetch user details for notification", e);
        }
        
        // Send notification to resource admin
        notificationService.createSystemNotification(
                "New booking created for " + resource.getName() + " by " + userDisplayName,
                "New booking from " + dateTimeUtils.formatDateTime(eventDTO.getStart()) + 
                " to " + dateTimeUtils.formatDateTime(eventDTO.getEnd())
        );
        
        return eventMapper.toDto(savedEvent);
    }

    /**
     * Update existing event
     */
    @Transactional
    public Optional<EventDTO> updateEvent(Long id, EventDTO eventDTO, String userId) {
        log.debug("Updating event with ID {} using DTO: {}", id, eventDTO);
        
        // Ensure time zone info for start and end times if provided
        if (eventDTO.getStart() != null) {
            eventDTO.setStart(dateTimeUtils.ensureTimeZone(eventDTO.getStart()));
        }
        
        if (eventDTO.getEnd() != null) {
            eventDTO.setEnd(dateTimeUtils.ensureTimeZone(eventDTO.getEnd()));
        }
        
        return eventRepository.findById(id)
                .map(existingEvent -> {
                    // Check if user has permission to update this event
                    if (!canModifyEvent(userId, existingEvent)) {
                        throw new AccessDeniedException("You don't have permission to update this event");
                    }
                    
                    // If changing resource, check if user has access to the new resource
                    if (eventDTO.getResourceId() != null && 
                        !existingEvent.getResource().getId().equals(eventDTO.getResourceId())) {
                        
                        Resource newResource = resourceRepository.findById(eventDTO.getResourceId())
                                .orElseThrow(() -> new EntityNotFoundException("Resource not found"));
                        
                        if (!canAccessResource(userId, newResource)) {
                            throw new AccessDeniedException("You don't have access to the new resource");
                        }
                    }
                    
                    // Update fields
                    if (eventDTO.getTitle() != null) {
                        existingEvent.setTitle(eventDTO.getTitle());
                    }
                    
                    if (eventDTO.getDescription() != null) {
                        existingEvent.setDescription(eventDTO.getDescription());
                    }
                    
                    // Only update start and end if provided
                    if (eventDTO.getStart() != null) {
                        existingEvent.setStart(eventDTO.getStart());
                    }
                    
                    if (eventDTO.getEnd() != null) {
                        existingEvent.setEnd(eventDTO.getEnd());
                    }
                    
                    // Validate time period after updates
                    if (existingEvent.getEnd().isBefore(existingEvent.getStart())) {
                        throw new IllegalStateException("End time must be after start time");
                    }
                    
                    // Check for time conflicts (excluding this event)
                    Long resourceId = eventDTO.getResourceId() != null ? eventDTO.getResourceId() : existingEvent.getResource().getId();
                    if (hasTimeConflict(resourceId, existingEvent.getStart(), existingEvent.getEnd(), id)) {
                        throw new IllegalStateException("The selected time period conflicts with existing bookings");
                    }
                    
                    // Update resource if changed
                    if (eventDTO.getResourceId() != null && !existingEvent.getResource().getId().equals(eventDTO.getResourceId())) {
                        Resource newResource = resourceRepository.findById(eventDTO.getResourceId())
                                .orElseThrow(() -> new EntityNotFoundException("Resource not found"));
                        
                        // Check if the new resource is in ACTIVE state
                        if (newResource.getStatus() != ResourceStatus.ACTIVE) {
                            throw new IllegalStateException("Cannot book a resource that is not in ACTIVE state. Current state: " + newResource.getStatus());
                        }
                        
                        existingEvent.setResource(newResource);
                    } else {
                        // Also check if the existing resource is still ACTIVE
                        if (existingEvent.getResource().getStatus() != ResourceStatus.ACTIVE) {
                            throw new IllegalStateException("Cannot update booking for a resource that is not in ACTIVE state. Current state: " + existingEvent.getResource().getStatus());
                        }
                    }
                    
                    // Update user (Keycloak ID) if provided and requester is admin
                    if (eventDTO.getUserId() != null && !eventDTO.getUserId().equals(existingEvent.getKeycloakId())) {
                        // Only admins can change the user
                        boolean isGlobalAdmin = keycloakService.hasGlobalAdminRole(userId);
                        boolean isFederationAdmin = keycloakService.isUserFederationAdmin(userId, existingEvent.getResource().getFederationId());
                        
                        if (!isGlobalAdmin && !isFederationAdmin) {
                            throw new AccessDeniedException("Only administrators can change the booking owner");
                        }
                        
                        // Verify the new user exists in Keycloak
                        keycloakService.getUserById(eventDTO.getUserId())
                            .orElseThrow(() -> new EntityNotFoundException("User not found with Keycloak ID: " + eventDTO.getUserId()));
                        
                        // Check if the new user is in the federation
                        if (!keycloakService.isUserInFederation(eventDTO.getUserId(), existingEvent.getResource().getFederationId())) {
                            throw new IllegalStateException("The new user must be a member of the resource's federation");
                        }
                            
                        existingEvent.setKeycloakId(eventDTO.getUserId());
                    }
                    
                    Event updatedEvent = eventRepository.save(existingEvent);
                    log.debug("Updated event: {}", updatedEvent);
                    return eventMapper.toDto(updatedEvent);
                });
    }

    /**
     * Delete event if the user has permission to do so
     */
    @Transactional
    public boolean deleteEvent(Long id, String userId) {
        Optional<Event> eventOpt = eventRepository.findById(id);
        
        if (!eventOpt.isPresent()) {
            return false;
        }
        
        Event event = eventOpt.get();
        
        // Check if user has permission to delete this event
        if (!canModifyEvent(userId, event)) {
            throw new AccessDeniedException("You don't have permission to delete this event");
        }
        
        eventRepository.deleteById(id);
        return true;
    }

    /**
     * Check if there's a time conflict for a resource booking
     */
    public boolean hasTimeConflict(Long resourceId, ZonedDateTime start, ZonedDateTime end, Long eventId) {
        // Normalize dates with time zone info
        ZonedDateTime normalizedStart = dateTimeUtils.ensureTimeZone(start);
        ZonedDateTime normalizedEnd = dateTimeUtils.ensureTimeZone(end);
        
        // Get the resource
        Resource resource = resourceRepository.findById(resourceId)
            .orElseThrow(() -> new EntityNotFoundException("Resource not found"));
        
        // Check conflicts for this specific resource
        List<Event> directConflicts = eventRepository.findConflictingEvents(
            resourceId, normalizedStart, normalizedEnd, eventId);
            
        if (!directConflicts.isEmpty()) {
            return true;
        }
        
        // Check if any parent resource is booked during this time
        Resource parent = resource.getParent();
        while (parent != null) {
            List<Event> parentConflicts = eventRepository.findConflictingEvents(
                parent.getId(), normalizedStart, normalizedEnd, eventId);
                
            if (!parentConflicts.isEmpty()) {
                return true;
            }
            
            parent = parent.getParent();
        }
        
        // Check if any child resource is booked during this time
        List<Resource> allSubResources = new ArrayList<>();
        resourceService.collectAllSubResources(resource, allSubResources);
        
        for (Resource subResource : allSubResources) {
            List<Event> childConflicts = eventRepository.findConflictingEvents(
                subResource.getId(), normalizedStart, normalizedEnd, eventId);
                
            if (!childConflicts.isEmpty()) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if a resource is available for booking (is in ACTIVE state) and user has access
     */
    public boolean isResourceAvailableForBooking(Long resourceId, String userId) {
        return resourceRepository.findById(resourceId)
                .map(resource -> 
                    resource.getStatus() == ResourceStatus.ACTIVE && canAccessResource(userId, resource)
                )
                .orElse(false);
    }

    /**
     * Check if user can modify an event (owns it or is admin of the resource's federation)
     */
    private boolean canModifyEvent(String userId, Event event) {
        // User is the owner of the event
        if (event.getKeycloakId().equals(userId)) {
            return true;
        }
        
        // Global admins can modify all events
        if (keycloakService.hasGlobalAdminRole(userId)) {
            return true;
        }
        
        // Federation admins can modify events in their federations
        String federationId = event.getResource().getFederationId();
        return keycloakService.isUserFederationAdmin(userId, federationId);
    }

    /**
     * Check if user can access an event (owns it or is in the resource's federation)
     */
    private boolean canAccessEvent(String userId, Event event) {
        // User is the owner of the event
        if (event.getKeycloakId().equals(userId)) {
            return true;
        }
        
        // Global admins can access all events
        if (keycloakService.hasGlobalAdminRole(userId)) {
            return true;
        }
        
        // Check if user is in the event resource's federation
        String federationId = event.getResource().getFederationId();
        return keycloakService.isUserInFederation(userId, federationId);
    }

    /**
     * Check if user can access a resource (is in the resource's federation)
     */
    private boolean canAccessResource(String userId, Resource resource) {
        // Global admins can access all resources
        if (keycloakService.hasGlobalAdminRole(userId)) {
            return true;
        }
        
        // Check if user is in the resource's federation
        return keycloakService.isUserInFederation(userId, resource.getFederationId());
    }
}   