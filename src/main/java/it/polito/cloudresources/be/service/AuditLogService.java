package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.config.datetime.DateTimeConfig;
import it.polito.cloudresources.be.model.AuditLog;
import it.polito.cloudresources.be.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

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

        AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .timestamp(timestamp)
                .logType(logType)
                .entityType(entityType)
                .action(action)
                .details(details)
                .userId(jwtAuth.getToken().getClaimAsString("sub"))
                .username(jwtAuth.getToken().getClaimAsString("preferred_username"))
                .federationName(jwtAuth.getToken().getClaimAsString("group"))
                .entityId(entityId)
                .severity(severity);

        auditLogRepository.save(builder.build());
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
}