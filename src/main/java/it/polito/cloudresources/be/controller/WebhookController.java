package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.dto.webhooks.WebhookConfigDTO;
import it.polito.cloudresources.be.dto.webhooks.WebhookConfigResponseDTO;
import it.polito.cloudresources.be.dto.webhooks.WebhookLogsResponseDTO;
import it.polito.cloudresources.be.model.WebhookLog;
import it.polito.cloudresources.be.repository.WebhookLogRepository;
import it.polito.cloudresources.be.service.WebhookService;
import it.polito.cloudresources.be.util.ControllerUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API controller for managing webhooks
 */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "API for managing webhooks")
@SecurityRequirement(name = "bearer-auth")
@PreAuthorize("hasRole('FEDERATION_ADMIN')")
public class WebhookController {

    private final WebhookService webhookService;
    private final WebhookLogRepository webhookLogRepository;
    private final ControllerUtils utils;
    
    /**
     * Get all webhooks
     */
    @GetMapping
    @Operation(summary = "Get all webhooks", description = "Retrieves all webhooks the user has access to")
    public ResponseEntity<List<WebhookConfigDTO>> getAllWebhooks(Authentication authentication) {
        String currentUserId = utils.getCurrentUserKeycloakId(authentication);
        List<WebhookConfigDTO> webhooks = webhookService.getAllWebhooks(currentUserId);
        return ResponseEntity.ok(webhooks);
    }
    
    /**
     * Get webhook by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get webhook by ID", description = "Retrieves a specific webhook by ID if the user has access")
    public ResponseEntity<WebhookConfigDTO> getWebhookById(
            @PathVariable Long id,
            Authentication authentication) {
        String currentUserId = utils.getCurrentUserKeycloakId(authentication);
        return webhookService.getWebhookById(id, currentUserId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Create a new webhook
     */
    @PostMapping
    @Operation(summary = "Create webhook", description = "Creates a new webhook configuration")
    public ResponseEntity<Object> createWebhook(
            @Valid @RequestBody WebhookConfigDTO webhookDTO,
            Authentication authentication) {
        try {
            String currentUserId = utils.getCurrentUserKeycloakId(authentication);
            WebhookConfigResponseDTO response = webhookService.createWebhook(webhookDTO, currentUserId);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (EntityNotFoundException e) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "An error occurred while creating the webhook: " + e.getMessage());
        }
    }
    
    /**
     * Update an existing webhook
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update webhook", description = "Updates an existing webhook configuration")
    public ResponseEntity<Object> updateWebhook(
            @PathVariable Long id,
            @Valid @RequestBody WebhookConfigDTO webhookDTO,
            Authentication authentication) {
        try {
            String currentUserId = utils.getCurrentUserKeycloakId(authentication);
            WebhookConfigDTO updatedWebhook = webhookService.updateWebhook(id, webhookDTO, currentUserId);
            
            return ResponseEntity.ok(updatedWebhook);
        } catch (EntityNotFoundException e) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "An error occurred while updating the webhook: " + e.getMessage());
        }
    }
    
    /**
     * Delete a webhook
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete webhook", description = "Deletes an existing webhook configuration")
    public ResponseEntity<Object> deleteWebhook(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            String currentUserId = utils.getCurrentUserKeycloakId(authentication);
            boolean deleted = webhookService.deleteWebhook(id, currentUserId);
            
            if (deleted) {
                return utils.createSuccessResponse("Webhook deleted successfully");
            } else {
                return utils.createErrorResponse(HttpStatus.NOT_FOUND, "Webhook not found");
            }
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "An error occurred while deleting the webhook: " + e.getMessage());
        }
    }
    
    /**
     * Test a webhook
     */
    @PostMapping("/{id}/test")
    @Operation(summary = "Test webhook", description = "Sends a test event to the webhook endpoint")
    public ResponseEntity<Object> testWebhook(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            String currentUserId = utils.getCurrentUserKeycloakId(authentication);
            boolean success = webhookService.testWebhook(id, currentUserId);
            
            if (success) {
                return utils.createSuccessResponse("Test webhook sent successfully");
            } else {
                return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                        "Failed to send test webhook");
            }
        } catch (EntityNotFoundException e) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "An error occurred while testing the webhook: " + e.getMessage());
        }
    }
    
    /**
     * Get logs for a webhook
     */
    @GetMapping("/{id}/logs")
    @Operation(summary = "Get webhook logs", description = "Retrieves execution logs for a specific webhook")
    public ResponseEntity<Object> getWebhookLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean success,
            Authentication authentication) {
        try {
            String currentUserId = utils.getCurrentUserKeycloakId(authentication);
            
            // Verify user has access to this webhook
            if (!webhookService.getWebhookById(id, currentUserId).isPresent()) {
                return utils.createErrorResponse(HttpStatus.FORBIDDEN, 
                        "You don't have permission to view logs for this webhook");
            }
            
            // Get logs with pagination
            PageRequest pageRequest = PageRequest.of(
                    page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            
            Page<WebhookLog> logs;
            if (success != null) {
                logs = webhookLogRepository.findByWebhookIdAndSuccess(id, success, pageRequest);
            } else {
                logs = webhookLogRepository.findByWebhookId(id, pageRequest);
            }
            
            return ResponseEntity.ok(
                    new ApiResponseDTO(true, "Webhook logs retrieved", logs));
            
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "An error occurred while retrieving webhook logs: " + e.getMessage());
        }
    }

    /**
 * Get all webhook logs accessible to the current user
 */
@GetMapping("/logs")
@Operation(summary = "Get all webhook logs", description = "Retrieves all webhook logs the user has access to")
public ResponseEntity<Object> getAllWebhookLogs(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Boolean success,
        @RequestParam(required = false) String query,
        Authentication authentication) {
    try {
        String currentUserId = utils.getCurrentUserKeycloakId(authentication);
        
        // Get all accessible webhook logs
        Page<WebhookLog> logs = webhookService.getAllAccessibleWebhookLogs(
                currentUserId, success, query, page, size);
        
        // Create response DTO with pagination metadata
        WebhookLogsResponseDTO responseDTO = new WebhookLogsResponseDTO(logs);
        
        return ResponseEntity.ok(
                new ApiResponseDTO(true, "Webhook logs retrieved", responseDTO));
        
    } catch (AccessDeniedException e) {
        return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
    } catch (Exception e) {
        return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                "An error occurred while retrieving webhook logs: " + e.getMessage());
    }
}
}