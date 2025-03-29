package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.config.datetime.DateTimeConfig;
import it.polito.cloudresources.be.model.AuditLog;
import it.polito.cloudresources.be.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Enhanced service for audit logging of application events
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final AuditLogRepository auditLogRepository;
    private final DateTimeConfig.DateTimeService dateTimeService;

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
        String userId;

        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .timestamp(timestamp)
                .logType(logType)
                .entityType(entityType)
                .action(action)
                .details(details)
                .severity(severity);

        if (entityId != null) {
            builder.entityId(entityId);
        }

        // Add user information from JWT token if available
        if (jwtAuth != null) {
            userId = jwtAuth.getToken().getClaimAsString("sub");
            builder.userId(userId);
            builder.username(jwtAuth.getToken().getClaimAsString("preferred_username"));
        } else {
            userId = "unknown";
            builder.username(userId);
        }

        log.info("[{}] USER: {} ACTION: {}", timestamp, userId, action.toString());

        auditLogRepository.save(builder.build());
    }

    /**
     * Get JWT Authentication token from security context
     */
    private JwtAuthenticationToken getJwtAuthenticationToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (JwtAuthenticationToken) auth;
    }
}