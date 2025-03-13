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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for event operations with consistent time zone handling
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final ResourceRepository resourceRepository;
    private final NotificationService notificationService;
    private final EventMapper eventMapper;
    private final DateTimeUtils dateTimeUtils;

    /**
     * Get all events
     */
    public List<EventDTO> getAllEvents() {
        return eventMapper.toDto(eventRepository.findAll());
    }

    /**
     * Get event by ID
     */
    public Optional<EventDTO> getEventById(Long id) {
        return eventRepository.findById(id)
                .map(eventMapper::toDto);
    }

    /**
     * Get events by resource
     */
    public List<EventDTO> getEventsByResource(Long resourceId) {
        return eventMapper.toDto(eventRepository.findByResourceId(resourceId));
    }

    /**
     * Get events by user
     */
    public List<EventDTO> getEventsByUser(Long userId) {
        return eventMapper.toDto(eventRepository.findByUserId(userId));
    }

    /**
     * Get events by user's Keycloak ID
     */
    public List<EventDTO> getEventsByUserKeycloakId(String keycloakId) {
        return eventMapper.toDto(eventRepository.findByUserKeycloakId(keycloakId));
    }

    /**
     * Get events by date range
     */
    public List<EventDTO> getEventsByDateRange(ZonedDateTime startDate, ZonedDateTime endDate) {
        // Make sure both dates have time zone info
        ZonedDateTime normalizedStartDate = dateTimeUtils.ensureTimeZone(startDate);
        ZonedDateTime normalizedEndDate = dateTimeUtils.ensureTimeZone(endDate);
        
        return eventMapper.toDto(
            eventRepository.findByDateRange(normalizedStartDate, normalizedEndDate)
        );
    }

    /**
     * Create new event
     */
    @Transactional
    public EventDTO createEvent(EventDTO eventDTO) {
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
        
        Event event = eventMapper.toEntity(eventDTO);
        Event savedEvent = eventRepository.save(event);
        
        log.debug("Saved event: {}", savedEvent);
        
        // Send notification to resource admin
        notificationService.createSystemNotification(
                "New booking created for " + resource.getName() + " by " + event.getUser().getFullName(),
                "New booking from " + dateTimeUtils.formatDateTime(eventDTO.getStart()) + 
                " to " + dateTimeUtils.formatDateTime(eventDTO.getEnd())
        );
        
        return eventMapper.toDto(savedEvent);
    }

    /**
     * Update existing event
     */
    @Transactional
    public Optional<EventDTO> updateEvent(Long id, EventDTO eventDTO) {
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
                    
                    // For complex objects like this, it's better to use selective updates rather than
                    // the full entity mapping from the DTO, because we need to preserve some relationships
                    Event updatedEvent = eventRepository.save(existingEvent);
                    log.debug("Updated event: {}", updatedEvent);
                    return eventMapper.toDto(updatedEvent);
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
        ZonedDateTime normalizedStart = dateTimeUtils.ensureTimeZone(start);
        ZonedDateTime normalizedEnd = dateTimeUtils.ensureTimeZone(end);
        
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
}