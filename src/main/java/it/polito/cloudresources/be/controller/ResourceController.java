package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.FederationDTO;
import it.polito.cloudresources.be.dto.ResourceDTO;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.service.FederationService;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.service.ResourceService;
import it.polito.cloudresources.be.util.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API controller for managing resources
 */
@RestController
@RequestMapping("/resources")
@RequiredArgsConstructor
@Tag(name = "Resources", description = "API for managing cloud resources")
@SecurityRequirement(name = "bearer-auth")
public class ResourceController {

    private final ResourceService resourceService;
    private final KeycloakService keycloakService;
    private final FederationService federationService;
    private final ControllerUtils utils;

    /**
     * Get all resources
     */
    @GetMapping
    @Operation(summary = "Get all resources", description = "Retrieves all resources with optional filtering by status, type, or federation")
    public ResponseEntity<List<ResourceDTO>> getAllResources(
            @RequestParam(required = false) ResourceStatus status,
            @RequestParam(required = false) Long typeId,
            @RequestParam(required = false) String federationId,
            Authentication authentication) {

        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        List<ResourceDTO> resources;
        
        // Check federation access if federationId is provided
        if (federationId != null) {
            // Check if user has access to this federation
            if (!keycloakService.isUserInFederation(currentUserKeycloakId, federationId) && 
                !keycloakService.hasGlobalAdminRole(currentUserKeycloakId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Get resources from this specific federation
            resources = resourceService.getResourcesByFederation(federationId);
        } else if (status != null) {
            resources = resourceService.getResourcesByStatus(status);
        } else if (typeId != null) {
            resources = resourceService.getResourcesByType(typeId);
        } else {
            resources = resourceService.getAllResources(currentUserKeycloakId);
        }
        
        return ResponseEntity.ok(resources);
    }

    /**
     * Get resource by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get resource by ID", description = "Retrieves a specific resource by its ID")
    public ResponseEntity<ResourceDTO> getResourceById(@PathVariable Long id) {
        return resourceService.getResourceById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create new resource
     */
    @PostMapping
    @PreAuthorize("hasRole('FEDERATION_ADMIN')")
    @Operation(summary = "Create resource", description = "Creates a new resource (Admin only)")
    public ResponseEntity<ResourceDTO> createResource(@Valid @RequestBody ResourceDTO resourceDTO, Authentication authentication) {
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        ResourceDTO createdResource = resourceService.createResource(resourceDTO, currentUserKeycloakId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdResource);
    }

    /**
     * Update existing resource
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FEDERATION_ADMIN')")
    @Operation(summary = "Update resource", description = "Updates an existing resource (Admin only)")
    public ResponseEntity<ResourceDTO> updateResource(
            @PathVariable Long id, 
            @Valid @RequestBody ResourceDTO resourceDTO,
            Authentication authentication) {
        
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        return resourceService.updateResource(id, resourceDTO, currentUserKeycloakId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update resource status
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('FEDERATION_ADMIN')")
    @Operation(summary = "Update resource status", description = "Updates the status of an existing resource (Admin only)")
    public ResponseEntity<ResourceDTO> updateResourceStatus(
            @PathVariable Long id, 
            @RequestParam ResourceStatus status,
            Authentication authentication) {
        
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        return resourceService.updateResourceStatus(id, status, currentUserKeycloakId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete resource
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('FEDERATION_ADMIN')")
    @Operation(summary = "Delete resource", description = "Deletes an existing resource (Admin only)")
    public ResponseEntity<Object> deleteResource(@PathVariable Long id, Authentication authentication) {
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);

        return resourceService.deleteResource(id, currentUserKeycloakId) ? 
            utils.createSuccessResponse("Resource deleted successfully") :
            utils.createErrorResponse(HttpStatus.NOT_FOUND, "Resource not found");
    }

    /**
     * Search resources
     */
    @GetMapping("/search")
    @Operation(summary = "Search resources", description = "Searches resources by name, specs, or location")
    public ResponseEntity<List<ResourceDTO>> searchResources(@RequestParam String query, Authentication authentication) {
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        List<String> federationIds = federationService.getUserFederations(currentUserKeycloakId)
        .stream().map(FederationDTO::getId).collect(Collectors.toList());

        List<ResourceDTO> resources = resourceService.searchResources(query, federationIds);
        return ResponseEntity.ok(resources);
    }
}
