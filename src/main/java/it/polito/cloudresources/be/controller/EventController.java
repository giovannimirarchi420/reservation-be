package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.config.datetime.DateTimeConfig;
import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.dto.EventDTO;
import it.polito.cloudresources.be.service.EventService;
import it.polito.cloudresources.be.util.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
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
    private final ControllerUtils utils;

    /**
     * Get all events
     */
    @GetMapping
    @Operation(summary = "Get all events", description = "Retrieves all events with optional filtering")
    public ResponseEntity<List<EventDTO>> getAllEvents(
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate,
            Authentication authentication) {

        List<EventDTO> events;
        if (resourceId != null) {
            events = eventService.getEventsByResource(resourceId);
        } else if (startDate != null && endDate != null) {
            events = eventService.getEventsByDateRange(startDate, endDate);
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
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
        List<EventDTO> events = eventService.getEventsByUserKeycloakId(keycloakId);
        return ResponseEntity.ok(events);
    }

    /**
     * Get event by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get event by ID", description = "Retrieves a specific event by its ID")
    public ResponseEntity<EventDTO> getEventById(@PathVariable Long id, Authentication authentication) {
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        boolean isAdmin = utils.isAdmin(authentication);

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
        
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
        eventDTO.setUserId(keycloakId);
        
        try {
            EventDTO createdEvent = eventService.createEvent(eventDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent);
        } catch (IllegalStateException e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
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
        
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        boolean isAdmin = utils.isAdmin(authentication);
        
        // Check if the event belongs to the current user or if they are an admin
        EventDTO existingEvent = eventService.getEventById(id).orElse(null);
        if (existingEvent == null) {
            return ResponseEntity.notFound().build();
        }
        
        if (!isAdmin && !existingEvent.getUserId().equals(currentUserKeycloakId)) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, "You can only update your own events");
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
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Delete event
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete event", description = "Deletes an existing booking event")
    public ResponseEntity<Object> deleteEvent(
            @PathVariable Long id,
            Authentication authentication) {
        
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        boolean isAdmin = utils.isAdmin(authentication);
        
        // Check if the event belongs to the current user or if they are an admin
        EventDTO existingEvent = eventService.getEventById(id).orElse(null);
        if (existingEvent == null) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, "Event not found");
        }
        
        if (!isAdmin && !existingEvent.getUserId().equals(currentUserKeycloakId)) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, "You can only delete your own events");
        }
        
        boolean deleted = eventService.deleteEvent(id);
        
        if (deleted) {
            return utils.createSuccessResponse("Event deleted successfully");
        } else {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, "Event not found");
        }
    }

    /**
     * Check for conflicting events
     */
    @GetMapping("/check-conflicts")
    @Operation(summary = "Check for conflicts", description = "Checks if an event conflicts with existing bookings")
    public ResponseEntity<ApiResponseDTO> checkConflicts(
            @RequestParam Long resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime end,
            @RequestParam(required = false) Long eventId) {
        
        // If start or end time is not provided, use the current time and an hour later
        ZonedDateTime effectiveStart = start != null ? start : ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);
        ZonedDateTime effectiveEnd = end != null ? end : ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID).plusHours(1);
        
        boolean hasConflicts = eventService.hasTimeConflict(resourceId, effectiveStart, effectiveEnd, eventId);
        
        return utils.createSuccessResponse(
                hasConflicts ? "The selected time period conflicts with existing bookings" : "No conflicts found",
                !hasConflicts
        );
    }
    
    /**
     * Check if a resource is available for booking
     */
    @GetMapping("/check-resource-availability")
    @Operation(summary = "Check resource availability", description = "Checks if a resource is available for booking (ACTIVE state)")
    public ResponseEntity<ApiResponseDTO> checkResourceAvailability(@RequestParam Long resourceId) {
        boolean isAvailable = eventService.isResourceAvailableForBooking(resourceId);
        
        if (isAvailable) {
            return utils.createSuccessResponse("Resource is available for booking", true);
        } else {
            return utils.createSuccessResponse("Resource is not available for booking. Only resources in ACTIVE state can be booked", false);
        }
    }
}