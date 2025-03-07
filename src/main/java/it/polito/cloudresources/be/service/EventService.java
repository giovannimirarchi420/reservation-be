package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.EventDTO;
import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.User;
import it.polito.cloudresources.be.repository.EventRepository;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for event operations
 */
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
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
        return userRepository.findByKeycloakId(keycloakId)
                .map(user -> eventRepository.findByUser(user).stream()
                        .map(this::convertToDTO)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    /**
     * Get events by date range
     */
    public List<EventDTO> getEventsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return eventRepository.findByDateRange(startDate, endDate).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create new event
     */
    @Transactional
    public EventDTO createEvent(EventDTO eventDTO) {
        // Validate time period
        if (eventDTO.getEnd().isBefore(eventDTO.getStart())) {
            throw new IllegalStateException("End time must be after start time");
        }
        
        // Check for time conflicts
        if (hasTimeConflict(eventDTO.getResourceId(), eventDTO.getStart(), eventDTO.getEnd(), null)) {
            throw new IllegalStateException("The selected time period conflicts with existing bookings");
        }
        
        Event event = convertToEntity(eventDTO);
        Event savedEvent = eventRepository.save(event);
        
        // Send notification to resource admin
        notificationService.createSystemNotification(
                "New booking created for " + event.getResource().getName() + " by " + event.getUser().getName(),
                "New booking from " + eventDTO.getStart() + " to " + eventDTO.getEnd()
        );
        
        return convertToDTO(savedEvent);
    }

    /**
     * Update existing event
     */
    @Transactional
    public Optional<EventDTO> updateEvent(Long id, EventDTO eventDTO) {
        // Validate time period
        if (eventDTO.getEnd().isBefore(eventDTO.getStart())) {
            throw new IllegalStateException("End time must be after start time");
        }
        
        // Check for time conflicts (excluding this event)
        if (hasTimeConflict(eventDTO.getResourceId(), eventDTO.getStart(), eventDTO.getEnd(), id)) {
            throw new IllegalStateException("The selected time period conflicts with existing bookings");
        }
        
        return eventRepository.findById(id)
                .map(existingEvent -> {
                    // Update fields
                    existingEvent.setTitle(eventDTO.getTitle());
                    existingEvent.setDescription(eventDTO.getDescription());
                    existingEvent.setStart(eventDTO.getStart());
                    existingEvent.setEnd(eventDTO.getEnd());
                    
                    // Update resource if changed
                    if (!existingEvent.getResource().getId().equals(eventDTO.getResourceId())) {
                        Resource newResource = resourceRepository.findById(eventDTO.getResourceId())
                                .orElseThrow(() -> new EntityNotFoundException("Resource not found"));
                        existingEvent.setResource(newResource);
                    }
                    
                    // Update user if changed (and provided)
                    if (eventDTO.getUserId() != null && !existingEvent.getUser().getKeycloakId().equals(eventDTO.getUserId())) {
                        Optional<User> newUser = userRepository.findByKeycloakId(eventDTO.getUserId());
                        newUser.ifPresent(existingEvent::setUser);
                    }
                    
                    return convertToDTO(eventRepository.save(existingEvent));
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
    public boolean hasTimeConflict(Long resourceId, LocalDateTime start, LocalDateTime end, Long eventId) {
        List<Event> conflictingEvents = eventRepository.findConflictingEvents(resourceId, start, end, eventId);
        return !conflictingEvents.isEmpty();
    }

    /**
     * Convert entity to DTO
     */
    private EventDTO convertToDTO(Event event) {
        EventDTO dto = modelMapper.map(event, EventDTO.class);
        dto.setResourceId(event.getResource().getId());
        dto.setResourceName(event.getResource().getName());
        dto.setUserId(event.getUser().getKeycloakId());
        dto.setUserName(event.getUser().getName());
        return dto;
    }

    /**
     * Convert DTO to entity
     */
    private Event convertToEntity(EventDTO dto) {
        Event event = modelMapper.map(dto, Event.class);
        
        // Set the resource
        Resource resource = resourceRepository.findById(dto.getResourceId())
                .orElseThrow(() -> new EntityNotFoundException("Resource not found"));
        event.setResource(resource);
        
        // Set the user by Keycloak ID
        User user = userRepository.findByKeycloakId(dto.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        event.setUser(user);
        
        return event;
    }
}
