package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import it.polito.cloudresources.be.dto.users.CreateUserDTO;
import it.polito.cloudresources.be.dto.users.SshKeyDTO;
import it.polito.cloudresources.be.dto.users.UpdateProfileDTO;
import it.polito.cloudresources.be.dto.users.UpdateUserDTO;
import it.polito.cloudresources.be.dto.users.UserDTO;
import it.polito.cloudresources.be.service.SiteService;
import it.polito.cloudresources.be.service.UserService;
import it.polito.cloudresources.be.util.ControllerUtils;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST API controller for managing users (fully integrated with Keycloak)
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "API for managing users")
@SecurityRequirement(name = "bearer-auth")
public class UserController {

    private final UserService userService;
    private final SiteService siteService;
    private final ControllerUtils utils;

    /**
     * Get all users with optional site filtering
     *
     * @param siteId Optional site ID to filter by
     * @param authentication User authentication object
     * @return List of users based on access permissions
     */
    @GetMapping
    @Operation(summary = "Get all users", description = "Retrieves all users with optional site filtering")
    public ResponseEntity<List<UserDTO>> getAllUsers(
            @RequestParam(required = false) String siteId,
            Authentication authentication) {

        try {
            String currentUserKeycloakId = utils.getCurrentUserKeycloakId(authentication);
            if (siteId != null) {
                return ResponseEntity.ok(siteService.getUsersInSite(siteId, currentUserKeycloakId));
            }
            return ResponseEntity.ok(userService.getAllUsers(currentUserKeycloakId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get user by ID (admin only)
     *
     * @param id The Keycloak user ID
     * @return The requested user or 404 if not found
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieves a specific user by their ID (Admin only)")
    public ResponseEntity<UserDTO> getUserById(@PathVariable String id, Authentication authentication) {
        try {
            String currentKeycloakUserId = utils.getCurrentUserKeycloakId(authentication);
            return ResponseEntity.ok(userService.getUserById(id, currentKeycloakUserId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get current user's profile
     *
     * @param authentication User authentication object
     * @return The current user's profile
     */
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Retrieves the profile of the currently authenticated user")
    public ResponseEntity<UserDTO> getCurrentUser(Authentication authentication) {
        try {
            String keycloakId = utils.getCurrentUserKeycloakId(authentication);
            return ResponseEntity.ok(userService.getUserById(keycloakId, keycloakId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create a new user (admin only)
     *
     * @param createUserDTO The user creation data
     * @return The created user or error response
     */
    @PostMapping
    @Operation(summary = "Create user", description = "Creates a new user (Admin only)")
    public ResponseEntity<Object> createUser(@Valid @RequestBody CreateUserDTO createUserDTO, Authentication authentication) {
        try {
            String currentKeycloakUserId = utils.getCurrentUserKeycloakId(authentication);
            UserDTO createdUser = userService.createUser(createUserDTO, createUserDTO.getPassword(), currentKeycloakUserId);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, "User does not have privileges");
        } catch (IllegalArgumentException e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, "Invalid input: " + e.getMessage());
        } catch (EntityExistsException e) {
            return utils.createErrorResponse(HttpStatus.CONFLICT, "Username or Email already used");
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, "Failed to create user: " + e.getMessage());
        }
    }

    /**
     * Update an existing user (admin only)
     *
     * @param id The Keycloak user ID
     * @param updateUserDTO The user update data
     * @return The updated user or error response
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update user", description = "Updates an existing user (Admin only)")
    public ResponseEntity<Object> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserDTO updateUserDTO,
            Authentication authentication) {
        try {
            String currentKeycloakUserId = utils.getCurrentUserKeycloakId(authentication);
            UserDTO updatedUser = userService.updateUser(id, updateUserDTO, currentKeycloakUserId);
            return ResponseEntity.ok(updatedUser);
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, "Failed to update user: " + e.getMessage());
        }
    }

    /**
     * Update current user's profile
     *
     * @param updateProfileDTO The profile update data
     * @param authentication User authentication object
     * @return The updated profile or error response
     */
    @PutMapping("/me")
    @Operation(summary = "Update profile", description = "Updates the current user's profile")
    public ResponseEntity<Object> updateProfile(
            @Valid @RequestBody UpdateProfileDTO updateProfileDTO,
            Authentication authentication) {
        try {
            String keycloakId = utils.getCurrentUserKeycloakId(authentication);
            UserDTO updatedUser = userService.updateProfile(keycloakId, updateProfileDTO);
            return ResponseEntity.ok(updatedUser);
        } catch (IllegalArgumentException e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (EntityNotFoundException e) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, "Failed to update profile: " + e.getMessage());
        }
    }

    /**
     * Delete user (admin only)
     *
     * @param id The Keycloak user ID
     * @return Success response or error
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user", description = "Deletes an existing user (Admin only)")
    public ResponseEntity<Object> deleteUser(@PathVariable String id, Authentication authentication) {
        try {
            String currentKeycloakUserId = utils.getCurrentUserKeycloakId(authentication);
            userService.deleteUser(id, currentKeycloakUserId);

            return utils.createSuccessResponse("User deleted successfully");
        } catch (AccessDeniedException e) {
            return utils.createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (EntityNotFoundException e) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete user: " + e.getMessage());
        }
    }

    /**
     * Get users by role (admin only)
     *
     * @param role The role to filter by
     * @return List of users with the specified role
     */
    @GetMapping("/by-role/{role}")
    @Operation(summary = "Get users by role", description = "Retrieves users with a specific role (Admin only)")
    public ResponseEntity<List<UserDTO>> getUsersByRole(@PathVariable String role, Authentication authentication) {
        try {
            String currentKeycloakUserId = utils.getCurrentUserKeycloakId(authentication);
            List<UserDTO> users = userService.getUsersByRole(role.toLowerCase(), currentKeycloakUserId);
            return ResponseEntity.ok(users);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get current user's SSH key
     *
     * @param authentication User authentication object
     * @return The SSH key or null
     */
    @GetMapping("/me/ssh-key")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get SSH key", description = "Retrieves the current user's SSH public key")
    public ResponseEntity<Object> getSshKey(Authentication authentication) {
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);

        Optional<String> sshKey = userService.getUserSshKey(keycloakId);

        SshKeyDTO response = new SshKeyDTO(sshKey.orElse(null));
        return ResponseEntity.ok(response);
    }

    /**
     * Delete current user's SSH key
     *
     * @param authentication User authentication object
     * @return Success response or error
     */
    @DeleteMapping("/me/ssh-key")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete SSH key", description = "Deletes the current user's SSH public key")
    public ResponseEntity<Object> deleteSshKey(Authentication authentication) {
        try {
            String keycloakId = utils.getCurrentUserKeycloakId(authentication);

            boolean deleted = userService.deleteUserSshKey(keycloakId);
            if (!deleted) {
                return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete SSH key");
            }

            return utils.createSuccessResponse("SSH key deleted successfully");
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete SSH key: " + e.getMessage());
        }
    }

    /**
     * Update current user's SSH key
     *
     * @param sshKeyDTO The SSH key DTO containing the new key
     * @param authentication User authentication object
     * @return Success response or error
     */
    @PutMapping("/me/ssh-key")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update SSH key", description = "Updates the current user's SSH public key")
    public ResponseEntity<Object> updateSshKey(
            @Valid @RequestBody SshKeyDTO sshKeyDTO,
            Authentication authentication) {
        try {
            String keycloakId = utils.getCurrentUserKeycloakId(authentication);
            
            // Create a profile DTO with just the SSH key field
            UpdateProfileDTO updateProfileDTO = new UpdateProfileDTO();
            updateProfileDTO.setSshPublicKey(sshKeyDTO.getSshPublicKey());
            
            userService.updateProfile(keycloakId, updateProfileDTO);
            return utils.createSuccessResponse("SSH key updated successfully");
        } catch (IllegalArgumentException e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (EntityNotFoundException e) {
            return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update SSH key: " + e.getMessage());
        }
    }
}