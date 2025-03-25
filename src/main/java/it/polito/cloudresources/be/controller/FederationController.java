package it.polito.cloudresources.be.controller;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    
    @GetMapping
    @Operation(summary = "Get all federations", description = "Retrieves all federations")
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
    
    @GetMapping("/{id}")
    @Operation(summary = "Get federation by ID", description = "Retrieves a specific federation by its ID")
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
    
    @PostMapping
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    @Operation(summary = "Create federation", description = "Creates a new federation")
    public ResponseEntity<FederationDTO> createFederation(
            @Valid @RequestBody FederationDTO federationDTO) {
        FederationDTO createdFederation = federationService.createFederation(federationDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdFederation);
    }
    
    @GetMapping("/{id}/users")
    @Operation(summary = "Get users in federation", description = "Retrieves all users in a federation")
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
    
    @PostMapping("/{id}/users/{userId}")
    @PreAuthorize("hasAnyRole('GLOBAL_ADMIN', 'FEDERATION_ADMIN')")
    @Operation(summary = "Add user to federation", description = "Adds a user to a federation")
    public ResponseEntity<Object> addUserToFederation(
            @PathVariable String id,
            @PathVariable String userId,
            Authentication authentication) {
        String currentUserId = utils.getCurrentUserKeycloakId(authentication);
        
        // Check if current user has permission to add users to this federation
        if (!keycloakService.hasGlobalAdminRole(currentUserId) && 
            !(keycloakService.isUserFederationAdmin(currentUserId, id))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        boolean added = keycloakService.addUserToFederation(userId, id);
        
        if (added) {
            return utils.createSuccessResponse("User added to federation successfully");
        } else {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to add user to federation");
        }
    }
    
    // More endpoints as needed
}