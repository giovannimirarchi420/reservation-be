package it.polito.cloudresources.be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Enhanced service for audit logging of application events
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Log resource access events
     */
    public void logResourceAccess(Long resourceId, String action) {
        String user = getCurrentUserInfo();
        String timestamp = getCurrentTimestamp();
        log.info("[{}] RESOURCE ACCESS: {} performed '{}' on resource ID {}", 
                timestamp, user, action, resourceId);
    }

    /**
     * Log admin actions
     */
    public void logAdminAction(String entity, String action, String details) {
        String user = getCurrentUserInfo();
        String timestamp = getCurrentTimestamp();
        log.info("[{}] ADMIN ACTION: {} performed '{}' on {} - Details: {}", 
                timestamp, user, action, entity, details);
    }

    /**
     * Log security events
     */
    public void logSecurityEvent(String event, String details) {
        String user = getCurrentUserInfo();
        String timestamp = getCurrentTimestamp();
        log.warn("[{}] SECURITY EVENT: {} - {} - Details: {}", 
                timestamp, user, event, details);
    }

    /**
     * Log user actions
     */
    public void logUserAction(String action, String details) {
        String user = getCurrentUserInfo();
        String timestamp = getCurrentTimestamp();
        log.info("[{}] USER ACTION: {} performed '{}' - Details: {}", 
                timestamp, user, action, details);
    }
    
    /**
     * Log system events (not associated with a specific user)
     */
    public void logSystemEvent(String event, String details) {
        String timestamp = getCurrentTimestamp();
        log.info("[{}] SYSTEM EVENT: {} - {}", timestamp, event, details);
    }
    
    /**
     * Get current user info from security context
     */
    private String getCurrentUserInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "anonymous";
        }
        
        if (auth instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) auth;
            String username = jwtAuth.getToken().getClaimAsString("preferred_username");
            String userId = jwtAuth.getToken().getSubject();
            return username + " (ID: " + userId + ")";
        }
        
        return auth.getName();
    }
    
    /**
     * Get current timestamp formatted for logging
     */
    private String getCurrentTimestamp() {
        return TIMESTAMP_FORMAT.format(LocalDateTime.now());
    }
}