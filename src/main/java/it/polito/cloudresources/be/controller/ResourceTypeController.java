package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.ResourceTypeDTO;
import it.polito.cloudresources.be.service.ResourceTypeService;
import it.polito.cloudresources.be.util.ControllerUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    private final ControllerUtils utils;

    /**
     * Get all resource types
     */
    @GetMapping
    @Operation(summary = "Get all resource types", description = "Retrieves all resource types with optional site filtering")
    public ResponseEntity<List<ResourceTypeDTO>> getAllResourceTypes(
            @RequestParam(required = false) String siteId,
            Authentication authentication) {
        try {
            String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
            return ResponseEntity.ok(resourceTypeService.getAllResourceTypes(currentUserKeycloakId, siteId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get resource type by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get resource type by ID", description = "Retrieves a specific resource type by its ID")
    public ResponseEntity<ResourceTypeDTO> getResourceTypeById(@PathVariable Long id, Authentication authentication) {
        try {
            String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
            return ResponseEntity.ok(resourceTypeService.getResourceTypeById(id, currentUserKeycloakId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create new resource type
     */
    @PostMapping
    @Operation(summary = "Create resource type", description = "Creates a new resource type")
    public ResponseEntity<ResourceTypeDTO> createResourceType(
        @Valid @RequestBody ResourceTypeDTO resourceTypeDTO,
        Authentication authentication) {
        try {
            String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
            ResourceTypeDTO createdType = resourceTypeService.createResourceType(resourceTypeDTO, currentUserKeycloakId);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdType);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update existing resource type
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update resource type", description = "Updates an existing resource type")
    public ResponseEntity<ResourceTypeDTO> updateResourceType(
            @PathVariable Long id, 
            @Valid @RequestBody ResourceTypeDTO resourceTypeDTO,
            Authentication authentication) {
        try {
            String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
            return ResponseEntity.ok(resourceTypeService.updateResourceType(id, resourceTypeDTO, currentUserKeycloakId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete resource type
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete resource type", description = "Deletes an existing resource type")
    public ResponseEntity<Object> deleteResourceType(@PathVariable Long id, Authentication authentication) {
        try {
            String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
            resourceTypeService.deleteResourceType(id, currentUserKeycloakId);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (IllegalStateException e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, "Resource type can't be deleted, a resource using this resource type exists");
        }
    }
}
