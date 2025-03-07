package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.dto.ResourceDTO;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * Get all resources
     */
    @GetMapping
    @Operation(summary = "Get all resources", description = "Retrieves all resources with optional filtering by status")
    public ResponseEntity<List<ResourceDTO>> getAllResources(
            @RequestParam(required = false) ResourceStatus status,
            @RequestParam(required = false) Long typeId) {

        List<ResourceDTO> resources;
        if (status != null) {
            resources = resourceService.getResourcesByStatus(status);
        } else if (typeId != null) {
            resources = resourceService.getResourcesByType(typeId);
        } else {
            resources = resourceService.getAllResources();
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
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create resource", description = "Creates a new resource (Admin only)")
    public ResponseEntity<ResourceDTO> createResource(@Valid @RequestBody ResourceDTO resourceDTO) {
        ResourceDTO createdResource = resourceService.createResource(resourceDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdResource);
    }

    /**
     * Update existing resource
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update resource", description = "Updates an existing resource (Admin only)")
    public ResponseEntity<ResourceDTO> updateResource(
            @PathVariable Long id, 
            @Valid @RequestBody ResourceDTO resourceDTO) {
        
        return resourceService.updateResource(id, resourceDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update resource status
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update resource status", description = "Updates the status of an existing resource (Admin only)")
    public ResponseEntity<ResourceDTO> updateResourceStatus(
            @PathVariable Long id, 
            @RequestParam ResourceStatus status) {
        
        return resourceService.updateResourceStatus(id, status)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete resource
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete resource", description = "Deletes an existing resource (Admin only)")
    public ResponseEntity<ApiResponseDTO> deleteResource(@PathVariable Long id) {
        boolean deleted = resourceService.deleteResource(id);
        
        if (deleted) {
            return ResponseEntity.ok(new ApiResponseDTO(true, "Resource deleted successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponseDTO(false, "Resource not found"));
        }
    }

    /**
     * Search resources
     */
    @GetMapping("/search")
    @Operation(summary = "Search resources", description = "Searches resources by name, specs, or location")
    public ResponseEntity<List<ResourceDTO>> searchResources(@RequestParam String query) {
        List<ResourceDTO> resources = resourceService.searchResources(query);
        return ResponseEntity.ok(resources);
    }
}
