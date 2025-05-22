package it.polito.cloudresources.be.util;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility class for JWT token operations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtUtils {

    private final JwtDecoder jwtDecoder;

    /**
     * Extract JWT token from HTTP request Authorization header
     *
     * @param request HTTP request
     * @return The JWT token string or null if not found
     */
    public String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Decode a JWT token string
     *
     * @param tokenString The JWT token string
     * @return Decoded Jwt object or null if token is invalid
     */
    public Jwt decode(String tokenString) {
        if (tokenString == null || tokenString.isEmpty()) {
            return null;
        }
        
        try {
            return jwtDecoder.decode(tokenString);
        } catch (Exception e) {
            log.debug("Failed to decode JWT token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract username from JWT token
     * 
     * @param jwt The JWT token
     * @return Username or null if not found
     */
    public String extractUsername(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        return jwt.getClaimAsString("preferred_username");
    }

    /**
     * Extract user ID (subject) from JWT token
     * 
     * @param jwt The JWT token
     * @return User ID or null if not found
     */
    public String extractUserId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        return jwt.getSubject();
    }

    /**
     * Extract roles from JWT token
     * 
     * @param jwt The JWT token
     * @return List of roles or empty list if not found
     */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Jwt jwt) {
        if (jwt == null) {
            return Collections.emptyList();
        }
        
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            return (List<String>) realmAccess.get("roles");
        }
        
        return Collections.emptyList();
    }
    
    /**
     * Create a JwtAuthenticationToken from a Jwt object
     * 
     * @param jwt The Jwt object
     * @return JwtAuthenticationToken or null if jwt is null
     */
    public JwtAuthenticationToken createAuthenticationToken(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        return new JwtAuthenticationToken(jwt);
    }
}
