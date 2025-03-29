package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.dto.AuditLogDTO;
import it.polito.cloudresources.be.dto.logs.AuditLogResponseDTO;
import it.polito.cloudresources.be.model.AuditLog;
import it.polito.cloudresources.be.service.AuditLogViewerService;
import it.polito.cloudresources.be.util.ControllerUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * REST API controller for accessing and searching audit logs
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "API for accessing and searching audit logs (Admin only)")
@SecurityRequirement(name = "bearer-auth")
@PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'FEDERATION_ADMIN')")
public class AuditLogController {

    private final AuditLogViewerService auditLogViewerService;
    private final ControllerUtils controllerUtils;

    /**
     * Get audit logs with optional filtering and pagination
     */
    @GetMapping
    @Operation(summary = "Get audit logs", description = "Retrieves audit logs with optional filtering and pagination (Admin only)")
    public ResponseEntity<ApiResponseDTO> getAuditLogs(
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
            @RequestParam(defaultValue = "20") int size) {

        try {
            // Start with all logs, then apply filters progressively
            List<AuditLogDTO> logs;
            
            // Apply pagination for initial fetch if no content search is needed
            // Otherwise, we'll filter first and paginate the final results
            if (query == null || query.trim().isEmpty()) {
                logs = auditLogViewerService.getAllLogs(page, size);
            } else {
                // If we have a query parameter, search logs by content first
                logs = auditLogViewerService.searchLogs(query, 0, Integer.MAX_VALUE);
            }
            
            // Apply filters in sequence (AND logic)
            if (entityType != null) {
                logs = logs.stream()
                    .filter(log -> entityType.equals(log.getEntityType()))
                    .toList();
                System.out.println(logs.toString());
            }
            
            if (entityId != null) {
                logs = logs.stream()
                    .filter(log -> entityId.equals(log.getEntityId()))
                    .toList();
            }
            
            if (username != null) {
                logs = logs.stream()
                    .filter(log -> username.equals(log.getUsername()))
                    .toList();
            }
            
            if (action != null) {
                AuditLog.LogAction logAction = parseLogAction(action);
                logs = logs.stream()
                    .filter(log -> logAction.equals(log.getAction()))
                    .toList();
            }
            
            if (severity != null) {
                AuditLog.LogSeverity logSeverity = parseLogSeverity(severity);
                logs = logs.stream()
                    .filter(log -> logSeverity.equals(log.getSeverity()))
                    .toList();
            }
            
            if (logType != null) {
                AuditLog.LogType logTypeEnum = parseLogType(logType);
                logs = logs.stream()
                    .filter(log -> logTypeEnum.equals(log.getLogType()))
                    .toList();
            }
            
            if (startDate != null && endDate != null) {
                logs = logs.stream()
                    .filter(log -> {
                        ZonedDateTime logTime = log.getTimestamp();
                        return (logTime.isEqual(startDate) || logTime.isAfter(startDate)) && 
                               (logTime.isEqual(endDate) || logTime.isBefore(endDate));
                    })
                    .toList();
            }

            // Apply paging to the result if we did content search first
            if (query != null && !query.trim().isEmpty() && logs.size() > size) {
                int fromIndex = page * size;
                int toIndex = Math.min(fromIndex + size, logs.size());
                
                if (fromIndex < logs.size()) {
                    logs = logs.subList(fromIndex, toIndex);
                } else {
                    logs = List.of(); // Empty list if page is out of bounds
                }
            }
            
            return ResponseEntity.ok(new ApiResponseDTO(true, "Logs retrieved successfully", new AuditLogResponseDTO(logs, logs.size())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponseDTO(false, e.getMessage()));
        }
    }

    /**
     * Get a single audit log by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get log by ID", description = "Retrieves a specific audit log by its ID (Admin only)")
    public ResponseEntity<AuditLogDTO> getLogById(@PathVariable Long id) {
        return auditLogViewerService.getLogById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Parse log action from string
     */
    private AuditLog.LogAction parseLogAction(String action) {
        try {
            return AuditLog.LogAction.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid action value. Expected one of: CREATE, UPDATE, DELETE");
        }
    }

    /**
     * Parse log severity from string
     */
    private AuditLog.LogSeverity parseLogSeverity(String severity) {
        try {
            return AuditLog.LogSeverity.valueOf(severity.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid severity value. Expected one of: INFO, WARNING, ERROR");
        }
    }

    /**
     * Parse log type from string
     */
    private AuditLog.LogType parseLogType(String logType) {
        try {
            return AuditLog.LogType.valueOf(logType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid log type value. Expected one of: USER, ADMIN");
        }
    }
}