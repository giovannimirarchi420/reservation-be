package it.polito.cloudresources.be.controller;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.FederationDTO;
import it.polito.cloudresources.be.dto.UserDTO;
import it.polito.cloudresources.be.service.FederationService;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.util.ControllerUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/federations")
@RequiredArgsConstructor
@Tag(name = "Federations", description = "API for managing federations")
@SecurityRequirement(name = "bearer-auth")
public class FederationController {

    private final FederationService federationService;
    private final KeycloakService keycloakService;
    private final ControllerUtils utils;
    
    /**
     * Get all federations - scoped by user access
     */
    @GetMapping
    @Operation(summary = "Get all federations", description = "Retrieves all federations. For regular users and federation admins, returns only federations they belong to. For global admins, returns all federations.")
    public ResponseEntity<List<FederationDTO>> getAllFederations(Authentication authentication) {
        String userId = utils.getCurrentUserKeycloakId(authentication);
        
        // Only global admins can see all federations
        if (!keycloakService.hasGlobalAdminRole(userId)) {
            List<String> userFederations = keycloakService.getUserFederations(userId);
            List<FederationDTO> federations = userFederations.stream()
                    .map(id -> federationService.getFederationById(id).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(federations);
        }
        
        return ResponseEntity.ok(federationService.getAllFederations());
    }
    
    /**
     * Get federation by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get federation by ID", description = "Retrieves a specific federation by its ID. User must belong to the federation or be a global admin.")
    public ResponseEntity<FederationDTO> getFederationById(@PathVariable String id, Authentication authentication) {
        String userId = utils.getCurrentUserKeycloakId(authentication);
        
        // Check if user has access to this federation
        if (!keycloakService.hasGlobalAdminRole(userId) && 
            !keycloakService.isUserInFederation(userId, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return federationService.getFederationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Create a new federation
     */
    @PostMapping
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    @Operation(summary = "Create federation", description = "Creates a new federation (Global Admin only)")
    public ResponseEntity<FederationDTO> createFederation(
            @Valid @RequestBody FederationDTO federationDTO) {
        FederationDTO createdFederation = federationService.createFederation(federationDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdFederation);
    }
    
    /**
     * Update an existing federation
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    @Operation(summary = "Update federation", description = "Updates an existing federation (Global Admin only)")
    public ResponseEntity<FederationDTO> updateFederation(
            @PathVariable String id,
            @Valid @RequestBody FederationDTO federationDTO) {
        return federationService.updateFederation(id, federationDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Delete a federation
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    @Operation(summary = "Delete federation", description = "Deletes an existing federation (Global Admin only)")
    public ResponseEntity<Object> deleteFederation(@PathVariable String id) {
        boolean deleted = federationService.deleteFederation(id);
        
        if (deleted) {
            return utils.createSuccessResponse("Federation deleted successfully");
        } else {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, 
                    "Federation not found or could not be deleted");
        }
    }
    
    /**
     * Get users in a federation
     */
    @GetMapping("/{id}/users")
    @Operation(summary = "Get users in federation", description = "Retrieves all users in a federation. Requires either Global Admin role or Federation Admin role for this federation.")
    public ResponseEntity<List<UserDTO>> getUsersInFederation(
            @PathVariable String id, 
            Authentication authentication) {
        String userId = utils.getCurrentUserKeycloakId(authentication);
        
        // Check if user has access to this federation
        if (!keycloakService.hasGlobalAdminRole(userId) && 
            !keycloakService.isUserFederationAdmin(userId, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        return ResponseEntity.ok(federationService.getUsersInFederation(id));
    }
    
    /**
     * Add user to a federation
     */
    @PostMapping("/{id}/users/{userId}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'FEDERATION_ADMIN')")
    @Operation(summary = "Add user to federation", description = "Adds a user to a federation. Requires either Global Admin role or Federation Admin role for this federation.")
    public ResponseEntity<Object> addUserToFederation(
            @PathVariable String id,
            @PathVariable String userId,
            Authentication authentication) {
        String currentUserId = utils.getCurrentUserKeycloakId(authentication);
        
        // Check if current user has permission to add users to this federation
        if (!keycloakService.hasGlobalAdminRole(currentUserId) && 
            !keycloakService.isUserFederationAdmin(currentUserId, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(utils.createErrorResponse(HttpStatus.FORBIDDEN, "You don't have permission to add users to this federation"));
        }
        
        boolean added = federationService.addUserToFederation(userId, id);
        
        if (added) {
            return utils.createSuccessResponse("User added to federation successfully");
        } else {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to add user to federation");
        }
    }
    
    /**
     * Remove user from a federation
     */
    @DeleteMapping("/{id}/users/{userId}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'FEDERATION_ADMIN')")
    @Operation(summary = "Remove user from federation", description = "Removes a user from a federation. Requires either Global Admin role or Federation Admin role for this federation.")
    public ResponseEntity<Object> removeUserFromFederation(
            @PathVariable String id,
            @PathVariable String userId,
            Authentication authentication) {
        String currentUserId = utils.getCurrentUserKeycloakId(authentication);
        
        // Check if current user has permission to remove users from this federation
        if (!keycloakService.hasGlobalAdminRole(currentUserId) && 
            !keycloakService.isUserFederationAdmin(currentUserId, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(utils.createErrorResponse(HttpStatus.FORBIDDEN, "You don't have permission to remove users from this federation"));
        }
        
        // Prevent removing yourself from a federation
        if (userId.equals(currentUserId) && keycloakService.isUserFederationAdmin(currentUserId, id)) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, 
                    "Federation admins cannot remove themselves from their federation");
        }
        
        boolean removed = federationService.removeUserFromFederation(userId, id);
        
        if (removed) {
            return utils.createSuccessResponse("User removed from federation successfully");
        } else {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to remove user from federation");
        }
    }
    
    /**
     * Make a user a federation admin
     */
    @PostMapping("/{id}/admins/{userId}")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    @Operation(summary = "Make user a federation admin", description = "Makes a user an admin of a federation (Global Admin only)")
    public ResponseEntity<Object> makeFederationAdmin(
            @PathVariable String id,
            @PathVariable String userId) {
        
        // Check if user is a member of the federation
        if (!keycloakService.isUserInFederation(userId, id)) {
            // Add user to federation if they're not already a member
            boolean added = keycloakService.addUserToFederation(userId, id);
            if (!added) {
                return utils.createErrorResponse(HttpStatus.BAD_REQUEST, 
                        "User must be a member of the federation before being made an admin");
            }
        }
        
        boolean success = keycloakService.makeFederationAdmin(userId, id);
        
        if (success) {
            return utils.createSuccessResponse("User successfully made federation admin");
        } else {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to make user a federation admin");
        }
    }
    
    /**
     * Remove federation admin status from a user
     */
    @DeleteMapping("/{id}/admins/{userId}")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    @Operation(summary = "Remove federation admin status", description = "Removes admin status from a user for a federation (Global Admin only)")
    public ResponseEntity<Object> removeFederationAdmin(
            @PathVariable String id,
            @PathVariable String userId) {
        
        boolean success = keycloakService.removeFederationAdmin(userId, id);
        
        if (success) {
            return utils.createSuccessResponse("Federation admin status successfully removed from user");
        } else {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to remove federation admin status from user");
        }
    }
    
    /**
     * Get federation admins
     */
    @GetMapping("/{id}/admins")
    @Operation(summary = "Get federation admins", description = "Retrieves all admin users for a federation")
    public ResponseEntity<List<UserDTO>> getFederationAdmins(
            @PathVariable String id,
            Authentication authentication) {
        String userId = utils.getCurrentUserKeycloakId(authentication);
        
        // Check if user has access to this federation
        if (!keycloakService.hasGlobalAdminRole(userId) && 
            !keycloakService.isUserInFederation(userId, id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        List<UserDTO> allFederationUsers = federationService.getUsersInFederation(id);
        List<UserDTO> adminUsers = allFederationUsers.stream()
                .filter(user -> keycloakService.isUserFederationAdmin(user.getId(), id))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(adminUsers);
    }
}