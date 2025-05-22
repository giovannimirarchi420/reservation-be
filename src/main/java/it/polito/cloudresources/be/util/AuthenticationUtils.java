package it.polito.cloudresources.be.util;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Utility class for authentication operations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationUtils {

    private final JwtUtils jwtUtils;

    /**
     * Get the current HTTP request
     *
     * @return HttpServletRequest or null if not available
     */
    public HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * Get JWT token from the current request
     *
     * @return JWT token string or null if not found
     */
    public String getCurrentToken() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            log.debug("No request context available");
            return null;
        }
        return jwtUtils.extractToken(request);
    }

    /**
     * Get decoded JWT from current request
     *
     * @return Decoded Jwt object or null if not available
     */
    public Jwt getCurrentJwt() {
        String token = getCurrentToken();
        return jwtUtils.decode(token);
    }

    /**
     * Get JwtAuthenticationToken from current request
     *
     * @return JwtAuthenticationToken or null if not available
     */
    public JwtAuthenticationToken getJwtAuthenticationToken() {
        Jwt jwt = getCurrentJwt();
        return jwtUtils.createAuthenticationToken(jwt);
    }

    /**
     * Get current username from JWT in request
     *
     * @return Username or null if not available
     */
    public String getCurrentUsername() {
        Jwt jwt = getCurrentJwt();
        return jwtUtils.extractUsername(jwt);
    }

    /**
     * Get current user ID from JWT in request
     *
     * @return User ID or null if not available
     */
    public String getCurrentUserId() {
        Jwt jwt = getCurrentJwt();
        return jwtUtils.extractUserId(jwt);
    }
}
