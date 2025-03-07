package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.dto.EventDTO;
import it.polito.cloudresources.be.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST API controller for managing booking events
 */
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "API for managing resource booking events")
@SecurityRequirement(name = "bearer-auth")
public class EventController {

    private final EventService eventService;

    /**
     * Get current user's Keycloak ID from JWT token
     */
    private String getCurrentUserKeycloakId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            return jwtAuth.getToken().getSubject();
        }
        return null;
    }

    /**
     * Get all events
     */
    @GetMapping
    @Operation(summary = "Get all events", description = "Retrieves all events with optional filtering")
    public ResponseEntity<List<EventDTO>> getAllEvents(
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication authentication) {

        String currentUserKeycloakId = getCurrentUserKeycloakId(authentication);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        List<EventDTO> events;
        if (resourceId != null) {
            events = eventService.getEventsByResource(resourceId);
        } else if (startDate != null && endDate != null) {
            events = eventService.getEventsByDateRange(startDate, endDate);
        } else if (!isAdmin) {
            // Regular users can only see their own events if no filters are applied
            events = eventService.getEventsByUserKeycloakId(currentUserKeycloakId);
        } else {
            events = eventService.getAllEvents();
        }
        
        return ResponseEntity.ok(events);
    }

    /**
     * Get my events
     */
    @GetMapping("/my-events")
    @Operation(summary = "Get current user's events", description = "Retrieves events for the currently authenticated user")
    public ResponseEntity<List<EventDTO>> getMyEvents(Authentication authentication) {
        String keycloakId = getCurrentUserKeycloakId(authentication);
        List<EventDTO> events = eventService.getEventsByUserKeycloakId(keycloakId);
        return ResponseEntity.ok(events);
    }

    /**
     * Get event by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get event by ID", description = "Retrieves a specific event by its ID")
    public ResponseEntity<EventDTO> getEventById(@PathVariable Long id, Authentication authentication) {
        String currentUserKeycloakId = getCurrentUserKeycloakId(authentication);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return eventService.getEventById(id)
                .filter(event -> isAdmin || event.getUserId().equals(currentUserKeycloakId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create new event
     */
    @PostMapping
    @Operation(summary = "Create event", description = "Creates a new booking event")
    public ResponseEntity<Object> createEvent(
            @Valid @RequestBody EventDTO eventDTO, 
            Authentication authentication) {
        
        String keycloakId = getCurrentUserKeycloakId(authentication);
        eventDTO.setUserId(keycloakId);
        
        try {
            EventDTO createdEvent = eventService.createEvent(eventDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDTO(false, e.getMessage()));
        }
    }

    /**
     * Update existing event
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update event", description = "Updates an existing booking event")
    public ResponseEntity<Object> updateEvent(
            @PathVariable Long id, 
            @Valid @RequestBody EventDTO eventDTO,
            Authentication authentication) {
        
        String currentUserKeycloakId = getCurrentUserKeycloakId(authentication);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        // Check if the event belongs to the current user or if they are an admin
        EventDTO existingEvent = eventService.getEventById(id).orElse(null);
        if (existingEvent == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (!isAdmin && !existingEvent.getUserId().equals(currentUserKeycloakId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponseDTO(false, "You can only update your own events"));
        }

        try {
            // Keep the original user if the updater is an admin
            if (!isAdmin) {
                eventDTO.setUserId(currentUserKeycloakId);
            } else if (eventDTO.getUserId() == null) {
                eventDTO.setUserId(existingEvent.getUserId());
            }

            return eventService.updateEvent(id, eventDTO)
                    .map(updatedEvent -> ResponseEntity.ok((Object)updatedEvent))
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponseDTO(false, e.getMessage()));
        }
    }

    /**
     * Delete event
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete event", description = "Deletes an existing booking event")
    public ResponseEntity<ApiResponseDTO> deleteEvent(
            @PathVariable Long id,
            Authentication authentication) {
        
        String currentUserKeycloakId = getCurrentUserKeycloakId(authentication);
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        // Check if the event belongs to the current user or if they are an admin
        EventDTO existingEvent = eventService.getEventById(id).orElse(null);
        if (existingEvent == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponseDTO(false, "Event not found"));
        }
        
        if (!isAdmin && !existingEvent.getUserId().equals(currentUserKeycloakId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ApiResponseDTO(false, "You can only delete your own events"));
        }
        
        boolean deleted = eventService.deleteEvent(id);
        
        if (deleted) {
            return ResponseEntity.ok(new ApiResponseDTO(true, "Event deleted successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponseDTO(false, "Event not found"));
        }
    }

    /**
     * Check for conflicting events
     */
    @GetMapping("/check-conflicts")
    @Operation(summary = "Check for conflicts", description = "Checks if an event conflicts with existing bookings")
    public ResponseEntity<ApiResponseDTO> checkConflicts(
            @RequestParam Long resourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Long eventId) {
        
        boolean hasConflicts = eventService.hasTimeConflict(resourceId, start, end, eventId);
        
        return ResponseEntity.ok(new ApiResponseDTO(
                !hasConflicts,
                hasConflicts ? "The selected time period conflicts with existing bookings" : "No conflicts found"
        ));
    }
}
