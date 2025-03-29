package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.dto.AuditLogDTO;
import it.polito.cloudresources.be.model.AuditLog;
import it.polito.cloudresources.be.service.AuditLogService;
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
     * Get all audit logs with pagination
     */
    @GetMapping
    @Operation(summary = "Get all logs", description = "Retrieves all audit logs with pagination (Admin only)")
    public ResponseEntity<List<AuditLogDTO>> getAllLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<AuditLogDTO> logs = auditLogViewerService.getAllLogs(page, size);

        return ResponseEntity.ok(logs);
    }

    /**
     * Get a single audit log by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get log by ID", description = "Retrieves a specific audit log by its ID (Admin only)")
    public ResponseEntity<AuditLogDTO> getLogById(@PathVariable Long id) {
        return auditLogViewerService.getLogById(id)
                .map(log -> {

                    return ResponseEntity.ok(log);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get logs by user ID
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get logs by user", description = "Retrieves audit logs for a specific user (Admin only)")
    public ResponseEntity<List<AuditLogDTO>> getLogsByUserId(@PathVariable String userId) {
        List<AuditLogDTO> logs = auditLogViewerService.getLogsByUserId(userId);

        return ResponseEntity.ok(logs);
    }

    /**
     * Get logs by federation ID
     */
    @GetMapping("/federation/{federationId}")
    @Operation(summary = "Get logs by federation", description = "Retrieves audit logs for a specific federation (Admin only)")
    public ResponseEntity<List<AuditLogDTO>> getLogsByFederationId(@PathVariable String federationId) {
        List<AuditLogDTO> logs = auditLogViewerService.getLogsByFederationId(federationId);

        return ResponseEntity.ok(logs);
    }

    /**
     * Get logs by entity type and ID
     */
    @GetMapping("/entity")
    @Operation(summary = "Get logs by entity", description = "Retrieves audit logs for a specific entity (Admin only)")
    public ResponseEntity<List<AuditLogDTO>> getLogsByEntity(
            @RequestParam String entityType,
            @RequestParam String entityId) {

        List<AuditLogDTO> logs = auditLogViewerService.getLogsByEntity(entityType, entityId);

        return ResponseEntity.ok(logs);
    }

    /**
     * Get logs by type
     */
    @GetMapping("/type/{logType}")
    @Operation(summary = "Get logs by type", description = "Retrieves audit logs of a specific type (Admin only)")
    public ResponseEntity<Object> getLogsByType(@PathVariable String logType) {
        List<AuditLogDTO> logs;
        if("admin".equalsIgnoreCase(logType)){
            logs = auditLogViewerService.getLogsByType(AuditLog.LogType.ADMIN);
        } else if ("user".equalsIgnoreCase(logType)) {
            logs = auditLogViewerService.getLogsByType(AuditLog.LogType.USER);
        } else {
            return controllerUtils.createErrorResponse(HttpStatus.BAD_REQUEST, "Expected log type to be 'admin' or 'user' ");
        }


        return ResponseEntity.status(HttpStatus.OK).body(logs);
    }

    /**
     * Get logs by action
     */
    @GetMapping("/action/{action}")
    @Operation(summary = "Get logs by action", description = "Retrieves audit logs for a specific action (Admin only)")
    public ResponseEntity<Object> getLogsByAction(@PathVariable String action) {
        List<AuditLogDTO> logs;
        if("create".equalsIgnoreCase(action)){
            logs = auditLogViewerService.getLogsByAction(AuditLog.LogAction.CREATE);
        } else if ("update".equalsIgnoreCase(action)) {
            logs = auditLogViewerService.getLogsByAction(AuditLog.LogAction.DELETE);
        } else if ("delete".equalsIgnoreCase(action)){
            logs = auditLogViewerService.getLogsByAction(AuditLog.LogAction.UPDATE);
        } else {
            return controllerUtils.createErrorResponse(HttpStatus.BAD_REQUEST, "Expected log action to be 'create' or 'update' or 'delete' ");
        }

        return ResponseEntity.status(HttpStatus.OK).body(logs);
    }

    /**
     * Get logs by severity
     */
    @GetMapping("/severity/{severity}")
    @Operation(summary = "Get logs by severity", description = "Retrieves audit logs with a specific severity (Admin only)")
    public ResponseEntity<List<AuditLogDTO>> getLogsBySeverity(@PathVariable AuditLog.LogSeverity severity) {
        List<AuditLogDTO> logs = auditLogViewerService.getLogsBySeverity(severity);

        return ResponseEntity.ok(logs);
    }

    /**
     * Get logs by date range
     */
    @GetMapping("/date-range")
    @Operation(summary = "Get logs by date range", description = "Retrieves audit logs within a specific date range (Admin only)")
    public ResponseEntity<List<AuditLogDTO>> getLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate) {

        List<AuditLogDTO> logs = auditLogViewerService.getLogsByDateRange(startDate, endDate);

        return ResponseEntity.ok(logs);
    }

    /**
     * Search logs by text
     */
    @GetMapping("/search")
    @Operation(summary = "Search logs", description = "Searches audit logs containing specific text in details (Admin only)")
    public ResponseEntity<List<AuditLogDTO>> searchLogs(@RequestParam String query) {
        List<AuditLogDTO> logs = auditLogViewerService.searchLogs(query);

        return ResponseEntity.ok(logs);
    }
}