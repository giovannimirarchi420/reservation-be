package it.polito.cloudresources.be.config.log;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Component for logging security events like authentication success and failures
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SecurityEventLogger {

    /**
     * Log successful authentication events
     */
    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        String ipAddress = getClientIP();
        
        if (auth instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) auth;
            String username = jwtAuth.getToken().getClaimAsString("preferred_username");
            String userId = jwtAuth.getToken().getSubject();
            
            log.debug("LOGIN SUCCESS: User '{}' (ID: {}) logged in successfully from IP {}",
                    username, userId, ipAddress);
        } else {
            log.debug("LOGIN SUCCESS: User '{}' logged in successfully from IP {}",
                    auth.getName(), ipAddress);
        }
    }

    /**
     * Log authentication failures
     */
    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        String ipAddress = getClientIP();
        String principal = event.getAuthentication().getName();
        
        log.warn("LOGIN FAILED: Failed login attempt for '{}' from IP {} - Reason: {}", 
                principal, ipAddress, event.getException().getMessage());
    }

    /**
     * Get client IP address from current request
     */
    private String getClientIP() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest)
                .map(this::extractIpAddress)
                .orElse("unknown");
    }

    /**
     * Extract real IP address, handling proxies
     */
    private String extractIpAddress(HttpServletRequest request) {
        String forwardedHeader = request.getHeader("X-Forwarded-For");
        if (forwardedHeader != null && !forwardedHeader.isEmpty()) {
            // Get first IP in case of multiple proxies
            return forwardedHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}