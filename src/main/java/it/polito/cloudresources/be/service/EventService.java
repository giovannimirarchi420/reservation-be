package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.config.DateTimeConfig;
import it.polito.cloudresources.be.dto.EventDTO;
import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.model.User;
import it.polito.cloudresources.be.repository.EventRepository;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for event operations with proper time zone handling
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    /**
     * Get all events
     */
    public List<EventDTO> getAllEvents() {
        return eventRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get event by ID
     */
    public Optional<EventDTO> getEventById(Long id) {
        return eventRepository.findById(id)
                .map(this::convertToDTO);
    }

    /**
     * Get events by resource
     */
    public List<EventDTO> getEventsByResource(Long resourceId) {
        return eventRepository.findByResourceId(resourceId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get events by user
     */
    public List<EventDTO> getEventsByUser(Long userId) {
        return eventRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get events by user's Keycloak ID
     */
    public List<EventDTO> getEventsByUserKeycloakId(String keycloakId) {
        // Ora usiamo la query ottimizzata del repository
        return eventRepository.findByUserKeycloakId(keycloakId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get events by date range
     */
    public List<EventDTO> getEventsByDateRange(ZonedDateTime startDate, ZonedDateTime endDate) {
        // Make sure both dates have time zone info
        ZonedDateTime normalizedStartDate = ensureTimeZone(startDate);
        ZonedDateTime normalizedEndDate = ensureTimeZone(endDate);
        
        return eventRepository.findByDateRange(normalizedStartDate, normalizedEndDate).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create new event
     */
    @Transactional
    public EventDTO createEvent(EventDTO eventDTO) {
        log.debug("Creating event with DTO: {}", eventDTO);
        
        // Set current time if not provided by frontend
        if (eventDTO.getStart() == null) {
            eventDTO.setStart(ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID));
        } else {
            // Ensure time zone info for start
            eventDTO.setStart(ensureTimeZone(eventDTO.getStart()));
        }
        
        if (eventDTO.getEnd() == null) {
            // Default to 1 hour later if end time not provided
            eventDTO.setEnd(ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID).plusHours(1));
        } else {
            // Ensure time zone info for end
            eventDTO.setEnd(ensureTimeZone(eventDTO.getEnd()));
        }
        
        // Validate time period
        if (eventDTO.getEnd().isBefore(eventDTO.getStart())) {
            throw new IllegalStateException("End time must be after start time");
        }
        
        // Check for time conflicts
        if (hasTimeConflict(eventDTO.getResourceId(), eventDTO.getStart(), eventDTO.getEnd(), null)) {
            throw new IllegalStateException("The selected time period conflicts with existing bookings");
        }
        
        // Check if the resource is in ACTIVE state
        Resource resource = resourceRepository.findById(eventDTO.getResourceId())
                .orElseThrow(() -> new EntityNotFoundException("Resource not found with ID: " + eventDTO.getResourceId()));
        
        if (resource.getStatus() != ResourceStatus.ACTIVE) {
            throw new IllegalStateException("Cannot book a resource that is not in ACTIVE state. Current state: " + resource.getStatus());
        }
        
        Event event = convertToEntity(eventDTO);
        Event savedEvent = eventRepository.save(event);
        
        log.debug("Saved event: {}", savedEvent);
        
        // Send notification to resource admin
        notificationService.createSystemNotification(
                "New booking created for " + event.getResource().getName() + " by " + event.getUser().getFullName(),
                "New booking from " + eventDTO.getStart() + " to " + eventDTO.getEnd()
        );
        
        return convertToDTO(savedEvent);
    }

    /**
     * Update existing event
     */
    @Transactional
    public Optional<EventDTO> updateEvent(Long id, EventDTO eventDTO) {
        log.debug("Updating event with ID {} using DTO: {}", id, eventDTO);
        
        // Ensure time zone info for start and end times if provided
        if (eventDTO.getStart() != null) {
            eventDTO.setStart(ensureTimeZone(eventDTO.getStart()));
        }
        
        if (eventDTO.getEnd() != null) {
            eventDTO.setEnd(ensureTimeZone(eventDTO.getEnd()));
        }
        
        return eventRepository.findById(id)
                .map(existingEvent -> {
                    // Update fields
                    existingEvent.setTitle(eventDTO.getTitle());
                    existingEvent.setDescription(eventDTO.getDescription());
                    
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
                    
                    // Update user if changed (and provided)
                    if (eventDTO.getUserId() != null && !existingEvent.getUser().getKeycloakId().equals(eventDTO.getUserId())) {
                        User newUser = userRepository.findByKeycloakId(eventDTO.getUserId())
                                .orElseThrow(() -> new EntityNotFoundException("User not found with Keycloak ID: " + eventDTO.getUserId()));
                        existingEvent.setUser(newUser);
                    }
                    
                    Event updatedEvent = eventRepository.save(existingEvent);
                    log.debug("Updated event: {}", updatedEvent);
                    return convertToDTO(updatedEvent);
                });
    }

    /**
     * Delete event
     */
    @Transactional
    public boolean deleteEvent(Long id) {
        if (eventRepository.existsById(id)) {
            eventRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Check if there's a time conflict for a resource booking
     */
    public boolean hasTimeConflict(Long resourceId, ZonedDateTime start, ZonedDateTime end, Long eventId) {
        // Ensure dates have time zone info
        ZonedDateTime normalizedStart = ensureTimeZone(start);
        ZonedDateTime normalizedEnd = ensureTimeZone(end);
        
        List<Event> conflictingEvents = eventRepository.findConflictingEvents(resourceId, normalizedStart, normalizedEnd, eventId);
        return !conflictingEvents.isEmpty();
    }
    
    /**
     * Check if a resource is available for booking (is in ACTIVE state)
     */
    public boolean isResourceAvailableForBooking(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .map(resource -> resource.getStatus() == ResourceStatus.ACTIVE)
                .orElse(false);
    }

    /**
     * Ensure a date has time zone info, applying the default if missing
     */
    private ZonedDateTime ensureTimeZone(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);
        }
        
        // A ZonedDateTime always has a zone, so we just normalize to the application's default time zone
        return dateTime.withZoneSameInstant(DateTimeConfig.DEFAULT_ZONE_ID);
    }

    /**
     * Convert entity to DTO
     * Modificato per evitare l'ambiguità di ModelMapper usando conversione manuale
     */
    private EventDTO convertToDTO(Event event) {
        // Non usiamo ModelMapper per evitare l'ambiguità
        EventDTO dto = new EventDTO();
        
        // Copiamo manualmente i campi
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setDescription(event.getDescription());
        dto.setStart(event.getStart());
        dto.setEnd(event.getEnd());
        
        // Copiamo i campi delle relazioni
        dto.setResourceId(event.getResource().getId());
        dto.setResourceName(event.getResource().getName());
        dto.setUserId(event.getUser().getKeycloakId());
        
        // Qui usiamo esplicitamente getFullName() per evitare ambiguità
        dto.setUserName(event.getUser().getUsername());
        
        return dto;
    }

    /**
     * Convert DTO to entity
     */
    private Event convertToEntity(EventDTO dto) {
        Event event = new Event();
        
        // Copiamo i campi semplici
        event.setId(dto.getId());
        event.setTitle(dto.getTitle());
        event.setDescription(dto.getDescription());
        event.setStart(dto.getStart());
        event.setEnd(dto.getEnd());
        
        // Set the resource
        Resource resource = resourceRepository.findById(dto.getResourceId())
                .orElseThrow(() -> new EntityNotFoundException("Resource not found with ID: " + dto.getResourceId()));
        event.setResource(resource);
        
        // Set the user by Keycloak ID
        log.debug("Looking for user with Keycloak ID: {}", dto.getUserId());
        User user = userRepository.findByKeycloakId(dto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found with Keycloak ID: " + dto.getUserId()));
        event.setUser(user);
        
        return event;
    }
}