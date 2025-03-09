package it.polito.cloudresources.be.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Service for audit logging of application events
 * TODO: Implement DB Audit logs
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditLogService {

    /**
     * Log resource access
     */
    public void logResourceAccess(Long resourceId, String action) {
        String user = getCurrentUserInfo();
        log.info("RESOURCE ACCESS: {} performed '{}' on resource ID {}", user, action, resourceId);
    }

    /**
     * Log admin actions
     */
    public void logAdminAction(String entity, String action, String details) {
        String user = getCurrentUserInfo();
        log.info("ADMIN ACTION: {} performed '{}' on {} - Details: {}", user, action, entity, details);
    }

    /**
     * Log security events
     */
    public void logSecurityEvent(String event, String details) {
        String user = getCurrentUserInfo();
        log.warn("SECURITY EVENT: {} - {} - Details: {}", user, event, details);
    }

    /**
     * Log user actions
     */
    public void logUserAction(String action, String details) {
        String user = getCurrentUserInfo();
        log.info("USER ACTION: {} performed '{}' - Details: {}", user, action, details);
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
}