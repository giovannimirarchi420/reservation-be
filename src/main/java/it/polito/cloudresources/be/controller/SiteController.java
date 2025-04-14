package it.polito.cloudresources.be.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.SiteDTO;
import it.polito.cloudresources.be.dto.users.UserDTO;
import it.polito.cloudresources.be.service.SiteService;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.util.ControllerUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/sites")
@RequiredArgsConstructor
@Tag(name = "Sites", description = "API for managing sites")
@SecurityRequirement(name = "bearer-auth")
public class SiteController {

    private final SiteService siteService;
    private final KeycloakService keycloakService;
    private final ControllerUtils utils;
    
    /**
     * Get all sites - scoped by user access
     */
    @GetMapping
    @Operation(summary = "Get all sites", description = "Retrieves all sites. For regular users and site admins, returns only sites they belong to. For global admins, returns all sites.")
    public ResponseEntity<List<SiteDTO>> getAllSites(Authentication authentication) {
        try {
            String userId = utils.getCurrentUserKeycloakId(authentication);
            List<SiteDTO> sites = siteService.getAllSites(userId);
            return ResponseEntity.ok(sites);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get site by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get site by ID", description = "Retrieves a specific site by its ID. User must belong to the site or be a global admin.")
    public ResponseEntity<SiteDTO> getSiteById(@PathVariable String id, Authentication authentication) {
        try {
            String userId = utils.getCurrentUserKeycloakId(authentication);
            return ResponseEntity.ok().body(siteService.getSiteById(id, userId));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Create a new site
     */
    @PostMapping
    @Operation(summary = "Create site", description = "Creates a new site (global admin or site admin only)")
    public ResponseEntity<SiteDTO> createSite (
            @Valid @RequestBody SiteDTO siteDTO, 
            Authentication authentication, 
            @RequestParam(required = false, defaultValue = "false") boolean isPrivate) {
        try {
            String userId = utils.getCurrentUserKeycloakId(authentication);
            SiteDTO createdSite = siteService.createSite(siteDTO, userId, isPrivate);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdSite);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /*
     * Update an existing site
    @PutMapping("/{id}")
    @Operation(summary = "Update site", description = "Updates an existing site (Global Admin only)")
    public ResponseEntity<SiteDTO> updateSite(
            @PathVariable String id,
            @Valid @RequestBody SiteDTO siteDTO) {

        return siteService.updateSite(id, siteDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
*/

    /**
     * Delete a site
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete site", description = "Deletes an existing site (Global Admin and Site admin only)")
    public ResponseEntity<Object> deleteSite(@PathVariable String id, Authentication authentication) {
        try {
            String userId = utils.getCurrentUserKeycloakId(authentication);
            siteService.deleteSite(id, userId);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get users in a site
     */
    @GetMapping("/{id}/users")
    @Operation(summary = "Get users in site", description = "Retrieves all users in a site. Requires either Global Admin role or Site Admin role for this site.")
    public ResponseEntity<List<UserDTO>> getUsersInSite (
            @PathVariable String id, 
            Authentication authentication) {
        try {
            String userId = utils.getCurrentUserKeycloakId(authentication);
            return ResponseEntity.ok(siteService.getUsersInSite(id, userId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Add user to a site
     */
    @PostMapping("/{id}/users/{userId}")
    @Operation(summary = "Add user to site", description = "Adds a user to a site. Requires either Global Admin role or Site Admin role for this site.")
    public ResponseEntity<Object> addUserToSite(
            @PathVariable String id,
            @PathVariable String userId,
            Authentication authentication) {
        try {
            String currentUserId = utils.getCurrentUserKeycloakId(authentication);
            siteService.addUserToSite(userId, id, currentUserId);
            return ResponseEntity.ok().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Remove user from a site
     */
    @DeleteMapping("/{id}/users/{userId}")
    @Operation(summary = "Remove user from site", description = "Removes a user from a site. Requires either Global Admin role or Site Admin role for this site.")
    public ResponseEntity<Object> removeUserFromSite(
            @PathVariable String id,
            @PathVariable String userId,
            Authentication authentication) {
        try {
            String currentUserId = utils.getCurrentUserKeycloakId(authentication);
            siteService.removeUserFromSite(userId, id, currentUserId);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalStateException e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
    
    /**
     * Make a user a site admin
     */
    @PostMapping("/{id}/admins/{userId}")
    @Operation(summary = "Make user a site admin", description = "Makes a user an admin of a site (Global Admin and Site Admin only)")
    public ResponseEntity<Object> makeSiteAdmin(
            @PathVariable String id,
            @PathVariable String userId,
            Authentication authentication) {
        try {
            String currentUserId = utils.getCurrentUserKeycloakId(authentication);
            keycloakService.makeSiteAdmin(userId, id, currentUserId);
            return ResponseEntity.ok().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Remove site admin status from a user
     */
    @DeleteMapping("/{id}/admins/{userId}")
    @Operation(summary = "Remove site admin status", description = "Removes admin status from a user for a site (Global Admin only)")
    public ResponseEntity<Object> removeSiteAdmin(
            @PathVariable String id,
            @PathVariable String userId,
            Authentication authentication) {
        try {
            String currentUserId = utils.getCurrentUserKeycloakId(authentication);
            keycloakService.removeSiteAdminRole(userId, id, currentUserId);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get site admins
     */
    @GetMapping("/{id}/admins")
    @Operation(summary = "Get site admins", description = "Retrieves all admin users for a site")
    public ResponseEntity<List<UserDTO>> getSiteAdmins(
            @PathVariable String id,
            Authentication authentication) {
        try {
            String userId = utils.getCurrentUserKeycloakId(authentication);
            List<UserDTO> adminUsers = siteService.getUsersSiteAdmins(id, userId);
            return ResponseEntity.ok(adminUsers);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}