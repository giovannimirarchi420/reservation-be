package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.config.datetime.DateTimeConfig;
import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.dto.EventDTO;
import it.polito.cloudresources.be.service.EventService;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.util.ControllerUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
@PreAuthorize("isAuthenticated()")
public class EventController {

    private final EventService eventService;
    private final KeycloakService  keycloakService;
    private final ControllerUtils utils;

    /**
     * Get all events
     */
    @GetMapping
    @Operation(summary = "Get all events", description = "Retrieves all events with optional filtering based on user's federation access")
    public ResponseEntity<List<EventDTO>> getAllEvents(
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate,
            @RequestParam(required = false) String federationId,
            Authentication authentication) {

        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        List<EventDTO> events;
        
        try {
            if (federationId != null) {
                // Check if user has access to this federation
                if (!keycloakService.isUserInFederation(currentUserKeycloakId, federationId) && 
                    !keycloakService.hasGlobalAdminRole(currentUserKeycloakId)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
                }
                events = eventService.getEventsByFederation(federationId, currentUserKeycloakId);
            } else if (resourceId != null) {
                events = eventService.getEventsByResource(resourceId, currentUserKeycloakId);
            } else if (startDate != null && endDate != null) {
                events = eventService.getEventsByDateRange(startDate, endDate, currentUserKeycloakId);
            } else {
                events = eventService.getAllEvents(currentUserKeycloakId);
            }
            
            return ResponseEntity.ok(events);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Get my events
     */
    @GetMapping("/my-events")
    @Operation(summary = "Get current user's events", description = "Retrieves events for the currently authenticated user")
    public ResponseEntity<List<EventDTO>> getMyEvents(Authentication authentication) {
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
        List<EventDTO> events = eventService.getEventsByUserKeycloakId(keycloakId, keycloakId);
        return ResponseEntity.ok(events);
    }

    /**
     * Get event by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get event by ID", description = "Retrieves a specific event by its ID if the user has access")
    public ResponseEntity<EventDTO> getEventById(@PathVariable Long id, Authentication authentication) {
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);

        try {
            return eventService.getEventById(id, currentUserKeycloakId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Create new event
     */
    @PostMapping
    @Operation(summary = "Create event", description = "Creates a new booking event for resources in user's federation")
    public ResponseEntity<Object> createEvent(
            @Valid @RequestBody EventDTO eventDTO, 
            Authentication authentication) {
        
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        // If no user ID is provided, set it to the current user
        if (eventDTO.getUserId() == null) {
            eventDTO.setUserId(keycloakId);
        }
        
        try {
            EventDTO createdEvent = eventService.createEvent(eventDTO, keycloakId);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdEvent);
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalStateException e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (EntityNotFoundException e) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Update existing event
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update event", description = "Updates an existing booking event if user has permission")
    public ResponseEntity<Object> updateEvent(
            @PathVariable Long id, 
            @Valid @RequestBody EventDTO eventDTO,
            Authentication authentication) {
        
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        try {
            return eventService.updateEvent(id, eventDTO, currentUserKeycloakId)
                    .map(updatedEvent -> ResponseEntity.ok((Object)updatedEvent))
                    .orElse(ResponseEntity.notFound().build());
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalStateException e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (EntityNotFoundException e) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Delete event
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete event", description = "Deletes an existing booking event if user has permission")
    public ResponseEntity<Object> deleteEvent(
            @PathVariable Long id,
            Authentication authentication) {
        
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        try {
            boolean deleted = eventService.deleteEvent(id, currentUserKeycloakId);
            
            if (deleted) {
                return utils.createSuccessResponse("Event deleted successfully");
            } else {
                return utils.createErrorResponse(HttpStatus.NOT_FOUND, "Event not found");
            }
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
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
            @RequestParam(required = false) Long eventId,
            Authentication authentication) {
        
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        // If start or end time is not provided, use the current time and an hour later
        ZonedDateTime effectiveStart = start != null ? start : ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);
        ZonedDateTime effectiveEnd = end != null ? end : ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID).plusHours(1);
        
        try {
            boolean hasConflicts = eventService.hasTimeConflict(resourceId, effectiveStart, effectiveEnd, eventId);
            
            return utils.createSuccessResponse(
                    hasConflicts ? "The selected time period conflicts with existing bookings" : "No conflicts found",
                    !hasConflicts
            );
        } catch (AccessDeniedException e) {
            return utils.createSuccessResponse("You don't have access to this resource", false);
        }
    }
    
    /**
     * Check if a resource is available for booking
     */
    @GetMapping("/check-resource-availability")
    @Operation(summary = "Check resource availability", description = "Checks if a resource is available for booking (ACTIVE state) and user has access")
    public ResponseEntity<ApiResponseDTO> checkResourceAvailability(
            @RequestParam Long resourceId,
            Authentication authentication) {
        
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        boolean isAvailable = eventService.isResourceAvailableForBooking(resourceId, currentUserKeycloakId);
        
        if (isAvailable) {
            return utils.createSuccessResponse("Resource is available for booking", true);
        } else {
            return utils.createSuccessResponse("Resource is not available for booking. Resource must be in ACTIVE state and you must have access to it.", false);
        }
    }
}