package it.polito.cloudresources.be.config.log;

import it.polito.cloudresources.be.util.AuthenticationUtils;
import it.polito.cloudresources.be.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filter for logging HTTP requests and responses with user information
 */
@Component
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final AuthenticationUtils authUtils;
    
    public RequestLoggingFilter(JwtUtils jwtUtils, AuthenticationUtils authUtils) {
        this.jwtUtils = jwtUtils;
        this.authUtils = authUtils;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        // Wrap request and response to cache their content
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Execute the rest of the filter chain
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // Extract user information from security context or JWT
            String username = extractUsername(requestWrapper);
            Collection<String> roles = extractRoles(requestWrapper);
            
            // Log the request
            logRequest(requestWrapper, username, roles, duration, responseWrapper.getStatus());
            
            // Log the response for specific content types
            logResponse(responseWrapper, requestWrapper.getMethod());
            
            // Copy content back to original response
            responseWrapper.copyBodyToResponse();
        }
    }
    
    /**
     * Log response details
     */
    private void logResponse(ContentCachingResponseWrapper response, String requestMethod) {
        // For REST endpoints, log response bodies
        String contentType = response.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            byte[] content = response.getContentAsByteArray();
            if (content.length > 0) {
                String body = new String(content);
                // Post/PUT/PATCH request responses are always logged
                if ("POST".equals(requestMethod) || "PUT".equals(requestMethod) || "PATCH".equals(requestMethod)) {
                    log.info("RESPONSE BODY: {}", body);
                } else if (log.isDebugEnabled()) {
                    // GET/DELETE responses are logged at debug level to avoid excessive logging
                    log.debug("RESPONSE BODY: {}", body);
                }
            }
        }
    }
    
    /**
     * Extract username from security context or JWT token in request
     */
    private String extractUsername(HttpServletRequest request) {
        // First try to get from SecurityContextHolder
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            if (auth instanceof JwtAuthenticationToken) {
                JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) auth;
                return jwtAuth.getToken().getClaimAsString("preferred_username");
            }
            
            return auth.getName();
        }
        
        // If auth is null, use AuthenticationUtils to try to get username from request
        String username = authUtils.getCurrentUsername();
        if (username != null) {
            return username;
        }
        
        // Final fallback: extract directly from request
        try {
            String token = jwtUtils.extractToken(request);
            Jwt jwt = jwtUtils.decode(token);
            if (jwt != null) {
                return jwtUtils.extractUsername(jwt);
            }
        } catch (Exception e) {
            log.debug("Failed to extract username from JWT token", e);
        }
        
        return "anonymous";
    }
    
    /**
     * Extract roles from security context or JWT token in request
     */
    private Collection<String> extractRoles(HttpServletRequest request) {
        // First try to get from SecurityContextHolder
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .collect(Collectors.toList());
        }
        
        // If auth is null, try to use AuthenticationUtils to get current JWT  
        JwtAuthenticationToken jwtAuth = authUtils.getJwtAuthenticationToken();
        if (jwtAuth != null && jwtAuth.getToken() instanceof Jwt jwt) {
            return jwtUtils.extractRoles(jwt);
        }
        return Collections.emptyList();
    }
    

    
    /**
     * Log request details
     */
    private void logRequest(ContentCachingRequestWrapper request, String username, 
                           Collection<String> roles, long duration, int status) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";
        
        // Create log entry
        log.info("REQUEST: {} {}{} | USER: {} | ROLES: {} | STATUS: {} | DURATION: {}ms",
                method, uri, queryString, username, roles, status, duration);
        
        // Always log request body for POST, PUT, PATCH methods
        if (("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) 
                && shouldLogRequestBody(request.getContentType())) {
            byte[] content = request.getContentAsByteArray();
            if (content.length > 0) {
                String body = new String(content);
                log.info("REQUEST BODY: {}", body);
            }
        }
        
        // For DEBUG level, log more details like headers
        if (log.isDebugEnabled()) {
            logHeaders(request);
        }
    }
    
    /**
     * Log request headers
     */
    private void logHeaders(ContentCachingRequestWrapper request) {
        StringBuilder headers = new StringBuilder();
        Collections.list(request.getHeaderNames()).forEach(headerName -> 
            Collections.list(request.getHeaders(headerName)).forEach(headerValue -> 
                headers.append(headerName).append("=").append(headerValue).append(", ")
            )
        );
        
        if (headers.length() > 0) {
            log.debug("REQUEST HEADERS: {}", headers.substring(0, headers.length() - 2));
        }
    }
    
    /**
     * Determine if we should log the request body based on content type
     */
    private boolean shouldLogRequestBody(String contentType) {
        if (contentType == null) {
            return false;
        }
        
        // Only log JSON, XML, text, and form data
        return contentType.contains("application/json") || 
               contentType.contains("application/xml") ||
               contentType.contains("text/") ||
               contentType.contains("application/x-www-form-urlencoded") ||
               contentType.contains("multipart/form-data");
    }
    
    /**
     * Skip logging for specific paths like actuator endpoints
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.contains("/actuator/") || 
               path.contains("/h2-console/") ||
               path.contains("/swagger-ui/") ||
               path.contains("/api-docs/");
    }
}