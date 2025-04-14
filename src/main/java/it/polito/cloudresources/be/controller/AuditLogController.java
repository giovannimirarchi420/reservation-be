package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.dto.AuditLogDTO;
import it.polito.cloudresources.be.dto.logs.EnhancedAuditLogResponseDTO;
import it.polito.cloudresources.be.service.AuditLogService;
import it.polito.cloudresources.be.util.ControllerUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;

/**
 * REST API controller for accessing and searching audit logs
 * Updated to use same authentication mechanism as other controllers
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "API for accessing and searching audit logs (Admin only)")
@SecurityRequirement(name = "bearer-auth")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final ControllerUtils utils;

    /**
     * Get audit logs with optional filtering, pagination and statistics
     */
    @GetMapping
    @Operation(summary = "Get audit logs", description = "Retrieves audit logs with optional filtering, pagination and statistics (Admin only)")
    public ResponseEntity<Object> getAuditLogs(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        try {
            // Get current user ID from authentication token
            String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
            
            if (currentUserKeycloakId == null) {
                return utils.createErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication required");
            }
            
            EnhancedAuditLogResponseDTO response = auditLogService.getAllAuditLogs(
                    currentUserKeycloakId,
                    entityType,
                    entityId,
                    username,
                    action,
                    severity,
                    logType,
                    query,
                    startDate,
                    endDate,
                    page,
                    size
            );
            
            return ResponseEntity.ok(new ApiResponseDTO(true, "Logs retrieved successfully", response));
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, "Access denied: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "An unexpected error occurred: " + e.getMessage());
        }
    }

    /**
     * Get a single audit log by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get log by ID", description = "Retrieves a specific audit log by its ID (Admin only)")
    public ResponseEntity<Object> getLogById(
            @PathVariable Long id,
            Authentication authentication) {
        
        try {
            // Get current user ID from authentication token
            String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
            
            if (currentUserKeycloakId == null) {
                return utils.createErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication required");
            }
            
            return ResponseEntity.ok(auditLogService.getLogById(id, currentUserKeycloakId));
                    
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, "Access denied: " + e.getMessage());
        } catch(EntityNotFoundException e ) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "An unexpected error occurred: " + e.getMessage());
        }
    }
}