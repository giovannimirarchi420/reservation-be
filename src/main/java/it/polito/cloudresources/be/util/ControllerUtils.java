package it.polito.cloudresources.be.util;

import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.service.MockKeycloakService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Utility class for common controller operations
 */
@Component
@AllArgsConstructor
@Slf4j
public class ControllerUtils {

    private final KeycloakService keycloakService;
    /**
     * Extracts the Keycloak ID from the authentication token
     *
     * @param authentication The authentication object
     * @return The Keycloak ID (subject) from the JWT token, or a mock ID in dev mode
     */
    public String getCurrentUserKeycloakId(Authentication authentication) {
        // For development environment, use the mock user ID
        if (keycloakService instanceof MockKeycloakService) {
            log.debug("Using mock Keycloak service");
            return ((MockKeycloakService) keycloakService).getCurrentUserKeycloakId();
        }
        
        // Normal JWT flow for production
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            return jwtAuth.getToken().getSubject();
        }
        
        return null;
    }

    /**
     * Creates an error response with the given status and message
     *
     * @param status The HTTP status code
     * @param message The error message
     * @return A ResponseEntity with an ApiResponseDTO containing the error details
     */
    public ResponseEntity<Object> createErrorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiResponseDTO(false, message));
    }

    /**
     * Creates a success response with a message
     *
     * @param message The success message
     * @return A ResponseEntity with an ApiResponseDTO containing the success message
     */
    public ResponseEntity<Object> createSuccessResponse(String message) {
        return ResponseEntity.ok(new ApiResponseDTO(true, message));
    }

    /**
     * Creates a success response with a message and data
     *
     * @param message The success message
     * @param data The data to include in the response
     * @return A ResponseEntity with an ApiResponseDTO containing the success message and data
     */
    public ResponseEntity<ApiResponseDTO> createSuccessResponse(String message, Object data) {
        ApiResponseDTO response = new ApiResponseDTO(true, message);
        response.setData(data);
        return ResponseEntity.ok(response);
    }
}