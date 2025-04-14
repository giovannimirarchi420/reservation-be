package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.config.datetime.DateTimeConfig;
import it.polito.cloudresources.be.dto.AuditLogDTO;
import it.polito.cloudresources.be.dto.logs.EnhancedAuditLogResponseDTO;
import it.polito.cloudresources.be.mapper.AuditLogMapper;
import it.polito.cloudresources.be.model.AuditLog;
import it.polito.cloudresources.be.repository.AuditLogRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Enhanced service for audit logging of application events
 * Updated with site-based access control
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final DateTimeConfig.DateTimeService dateTimeService;
    private final KeycloakService keycloakService;
    private final AuditLogMapper auditLogMapper;

    @Async
    public void logCrudAction(AuditLog.LogType type, AuditLog.LogAction action, AuditLog.LogEntity entity, String details) {
        // Save to database
        createAndSaveAuditLogCRUD(
                type,
                entity.getEntityType(),
                action,
                details,
                entity.getEntityId(),
                AuditLog.LogSeverity.INFO
        );
    }

    /**
     * Centralized method to create and save AuditLog entities
     */
    private void createAndSaveAuditLogCRUD (
            AuditLog.LogType logType,
            String entityType,
            AuditLog.LogAction action,
            String details,
            String entityId,
            AuditLog.LogSeverity severity) {

        JwtAuthenticationToken jwtAuth = getJwtAuthenticationToken();
        ZonedDateTime timestamp = dateTimeService.getCurrentDateTime();

        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .timestamp(timestamp)
                .logType(logType)
                .entityType(entityType)
                .action(action)
                .details(details)
                .username(jwtAuth.getToken().getClaimAsString("preferred_username"))
                .siteName(jwtAuth.getToken().getClaimAsString("group"))
                .entityId(entityId)
                .severity(severity);

        auditLogRepository.save(builder.build());
    }

    /**
     * Get all audit logs with pagination and filtering, respecting access restrictions
     * 
     * @param userId The ID of the user requesting the logs
     * @param entityType Optional filter for entity type
     * @param entityId Optional filter for entity ID
     * @param username Optional filter for username
     * @param action Optional filter for action type
     * @param severity Optional filter for severity
     * @param logType Optional filter for log type
     * @param query Optional text query for searching log details
     * @param startDate Optional start date for filtering logs
     * @param endDate Optional end date for filtering logs
     * @param page Page number (0-based)
     * @param size Page size
     * @return Enhanced response with logs and statistics
     */
    public EnhancedAuditLogResponseDTO getAllAuditLogs(
            String userId,
            String entityType,
            String entityId,
            String username,
            String action,
            String severity,
            String logType,
            String query,
            ZonedDateTime startDate,
            ZonedDateTime endDate,
            int page,
            int size) {
        
        // Check authorization - only global admins and site admins can access logs
        if (!keycloakService.hasGlobalAdminRole(userId) && 
            keycloakService.getUserAdminGroups(userId).isEmpty()) {
            throw new AccessDeniedException("Only administrators can access audit logs");
        }
        
        // Start with all logs or search by query
        List<AuditLogDTO> logs;
        
        // Apply pagination for initial fetch if no content search is needed
        // Otherwise, we'll filter first and paginate the final results
        if (query == null || query.trim().isEmpty()) {
            logs = getAllLogs(page, size);
        } else {
            // If we have a query parameter, search logs by content first
            logs = searchLogs(query, 0, Integer.MAX_VALUE);
        }
        
        // Apply filters in sequence (AND logic)
        if (entityType != null) {
            logs = logs.stream()
                .filter(log -> entityType.equals(log.getEntityType()))
                .toList();
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
                logs = new ArrayList<>(); // Empty list if page is out of bounds
            }
        }
        
        // Get enhanced response with statistics
        return getLogStatistics(logs);
    }

    /**
     * Get all audit logs with pagination
     */
    private List<AuditLogDTO> getAllLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return auditLogRepository.findAll(pageable)
                .stream()
                .map(auditLogMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get a single audit log by ID
     */
    public AuditLogDTO getLogById(Long id, String userId) {
        // Check authorization - only global admins and site admins can access logs
        if (!keycloakService.hasGlobalAdminRole(userId) && 
            keycloakService.getUserAdminGroups(userId).isEmpty()) {
            throw new AccessDeniedException("Only administrators can access audit logs");
        }
        
        Optional<AuditLogDTO> logOpt = auditLogRepository.findById(id)
                .map(auditLogMapper::toDto);
        
        if(!logOpt.isPresent()) {
            throw new EntityNotFoundException("Log not found with ID: " + id);
        }

        AuditLogDTO log = logOpt.get();

        // For site admins, check if log belongs to their site
        if (!keycloakService.hasGlobalAdminRole(userId)) {
            List<String> adminSites = keycloakService.getUserAdminGroups(userId);
            if (!adminSites.contains(log.getSiteName())) {
                throw new AccessDeniedException("Not authorized to access this log");
            }
        }
        
        return log;
    }

    /**
     * Search logs by details text
     */
    private List<AuditLogDTO> searchLogs(String searchText, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

        return auditLogRepository.findByDetailsContainingIgnoreCase(searchText, pageable)
                .stream()
                .map(auditLogMapper::toDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get log statistics
     */
    private EnhancedAuditLogResponseDTO getLogStatistics(List<AuditLogDTO> logs) {
        EnhancedAuditLogResponseDTO response = new EnhancedAuditLogResponseDTO(logs);
        
        // Get total count
        response.setTotalElements(auditLogRepository.count());
        
        // Get admin logs count
        response.setAdminLogsCount(auditLogRepository.countByLogType(AuditLog.LogType.ADMIN));
        
        // Get user logs count
        response.setUserLogsCount(auditLogRepository.countByLogType(AuditLog.LogType.USER));
        
        // Get error logs count
        response.setErrorLogsCount(auditLogRepository.countBySeverity(AuditLog.LogSeverity.ERROR));
        
        return response;
    }

    /**
     * Get JWT Authentication token from security context
     */
    private JwtAuthenticationToken getJwtAuthenticationToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken) {
            return (JwtAuthenticationToken) auth;
        }
        return null;
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