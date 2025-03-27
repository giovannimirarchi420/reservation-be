package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.ResourceTypeDTO;
import it.polito.cloudresources.be.service.FederationService;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.service.ResourceTypeService;
import it.polito.cloudresources.be.util.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST API controller for managing resource types
 */
@RestController
@RequestMapping("/resource-types")
@RequiredArgsConstructor
@Tag(name = "Resource Types", description = "API for managing resource types (Admin only)")
@SecurityRequirement(name = "bearer-auth")
public class ResourceTypeController {

    private final ResourceTypeService resourceTypeService;
    private final FederationService federationService;
    private final KeycloakService keycloakService;
    private final ControllerUtils utils;

    /**
     * Get all resource types
     */
    @GetMapping
    @Operation(summary = "Get all resource types", description = "Retrieves all resource types with optional federation filtering")
    public ResponseEntity<List<ResourceTypeDTO>> getAllResourceTypes(
            @RequestParam(required = false) String federationId,
            Authentication authentication) {
        
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        // If federationId is provided, check access and filter accordingly
        if (federationId != null) {
            // Check if user has access to this federation
            if (!keycloakService.isUserInFederation(currentUserKeycloakId, federationId) && 
                !keycloakService.hasGlobalAdminRole(currentUserKeycloakId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            
            // Return resource types for this federation only
            return ResponseEntity.ok(resourceTypeService.getAllResourceTypes(Arrays.asList(federationId)));
        }
        
        // Default behavior: return all accessible resource types
        if (keycloakService.hasGlobalAdminRole(currentUserKeycloakId)) {
            return ResponseEntity.ok(resourceTypeService.getAllResourceTypes());
        }
    
        List<String> federationIds = federationService.getUserFederations(currentUserKeycloakId)
            .stream().map(federationDto -> federationDto.getId()).collect(Collectors.toList());
    
        return ResponseEntity.ok(resourceTypeService.getAllResourceTypes(federationIds));
    }

    /**
     * Get resource type by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get resource type by ID", description = "Retrieves a specific resource type by its ID")
    public ResponseEntity<ResourceTypeDTO> getResourceTypeById(@PathVariable Long id) {
        return resourceTypeService.getResourceTypeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create new resource type
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('FEDERATION_ADMIN', 'GLOBAL_ADMIN')")
    @Operation(summary = "Create resource type", description = "Creates a new resource type")
    public ResponseEntity<ResourceTypeDTO> createResourceType(
        @Valid @RequestBody ResourceTypeDTO resourceTypeDTO,
        Authentication authentication) {
        System.out.println("TEST: " + resourceTypeDTO.toString());
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        ResourceTypeDTO createdType = resourceTypeService.createResourceType(resourceTypeDTO, currentUserKeycloakId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdType);
    }

    /**
     * Update existing resource type
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FEDERATION_ADMIN', 'GLOBAL_ADMIN')")
    @Operation(summary = "Update resource type", description = "Updates an existing resource type")
    public ResponseEntity<ResourceTypeDTO> updateResourceType(
            @PathVariable Long id, 
            @Valid @RequestBody ResourceTypeDTO resourceTypeDTO,
            Authentication authentication) {
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        return resourceTypeService.updateResourceType(id, resourceTypeDTO, currentUserKeycloakId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete resource type
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('FEDERATION_ADMIN', 'GLOBAL_ADMIN')")
    @Operation(summary = "Delete resource type", description = "Deletes an existing resource type")
    public ResponseEntity<Object> deleteResourceType(@PathVariable Long id, Authentication authentication) {
        String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
        return resourceTypeService.deleteResourceType(id, currentUserKeycloakId) ? 
            utils.createSuccessResponse("Resource type deleted successfully") :
            utils.createErrorResponse(HttpStatus.BAD_REQUEST, "Resource type not found or in use or not authorized");
    }
}
