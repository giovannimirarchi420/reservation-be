package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.dto.ResourceTypeDTO;
import it.polito.cloudresources.be.service.ResourceTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API controller for managing resource types
 */
@RestController
@RequestMapping("/resource-types")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Resource Types", description = "API for managing resource types (Admin only)")
@SecurityRequirement(name = "bearer-auth")
public class ResourceTypeController {

    private final ResourceTypeService resourceTypeService;

    /**
     * Get all resource types
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    @Operation(summary = "Get all resource types", description = "Retrieves all resource types")
    public ResponseEntity<List<ResourceTypeDTO>> getAllResourceTypes() {
        return ResponseEntity.ok(resourceTypeService.getAllResourceTypes());
    }

    /**
     * Get resource type by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
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
    @Operation(summary = "Create resource type", description = "Creates a new resource type")
    public ResponseEntity<ResourceTypeDTO> createResourceType(@Valid @RequestBody ResourceTypeDTO resourceTypeDTO) {
        ResourceTypeDTO createdType = resourceTypeService.createResourceType(resourceTypeDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdType);
    }

    /**
     * Update existing resource type
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update resource type", description = "Updates an existing resource type")
    public ResponseEntity<ResourceTypeDTO> updateResourceType(
            @PathVariable Long id, 
            @Valid @RequestBody ResourceTypeDTO resourceTypeDTO) {
        
        return resourceTypeService.updateResourceType(id, resourceTypeDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete resource type
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete resource type", description = "Deletes an existing resource type")
    public ResponseEntity<ApiResponseDTO> deleteResourceType(@PathVariable Long id) {
        boolean deleted = resourceTypeService.deleteResourceType(id);
        
        if (deleted) {
            return ResponseEntity.ok(new ApiResponseDTO(true, "Resource type deleted successfully"));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponseDTO(false, "Resource type not found or in use"));
        }
    }
}
