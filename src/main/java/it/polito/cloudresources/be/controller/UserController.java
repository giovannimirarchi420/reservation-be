package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import it.polito.cloudresources.be.dto.users.CreateUserDTO;
import it.polito.cloudresources.be.dto.users.SshKeyDTO;
import it.polito.cloudresources.be.dto.users.UpdateProfileDTO;
import it.polito.cloudresources.be.dto.users.UpdateUserDTO;
import it.polito.cloudresources.be.dto.users.UserDTO;
import it.polito.cloudresources.be.service.FederationService;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.service.UserService;
import it.polito.cloudresources.be.util.ControllerUtils;
import it.polito.cloudresources.be.util.SshKeyValidator;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    private final FederationService federationService;
    private final KeycloakService keycloakService;
    private final ControllerUtils utils;
    private final SshKeyValidator sshKeyValidator;

    /**
     * Get all users with optional federation filtering
     *
     * @param federationId Optional federation ID to filter by
     * @param authentication User authentication object
     * @return List of users based on access permissions
     */
    @GetMapping
    @PreAuthorize("hasRole('FEDERATION_ADMIN')")
    @Operation(summary = "Get all users", description = "Retrieves all users with optional federation filtering")
    public ResponseEntity<List<UserDTO>> getAllUsers(
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

            // Return users for this federation only
            return ResponseEntity.ok(federationService.getUsersInFederation(federationId));
        }

        // Default behavior: global admins see all, federation admins see their federation users
        if (keycloakService.hasGlobalAdminRole(currentUserKeycloakId)) {
            return ResponseEntity.ok(userService.getAllUsers());
        } else {
            // Get all federations where user is an admin
            List<String> adminFederations = keycloakService.getUserAdminFederations(currentUserKeycloakId);

            // If user is not an admin of any federation, return empty list
            if (adminFederations.isEmpty()) {
                return ResponseEntity.ok(new ArrayList<>());
            }

            // Collect users from all federations where user is an admin
            List<UserDTO> users = new ArrayList<>();
            for (String fedId : adminFederations) {
                users.addAll(federationService.getUsersInFederation(fedId));
            }

            return ResponseEntity.ok(users);
        }
    }

    /**
     * Get user by ID (admin only)
     *
     * @param id The Keycloak user ID
     * @return The requested user or 404 if not found
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('FEDERATION_ADMIN')")
    @Operation(summary = "Get user by ID", description = "Retrieves a specific user by their ID (Admin only)")
    public ResponseEntity<UserDTO> getUserById(@PathVariable String id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
        return userService.getUserById(keycloakId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new user (admin only)
     *
     * @param createUserDTO The user creation data
     * @return The created user or error response
     */
    @PostMapping
    @PreAuthorize("hasRole('FEDERATION_ADMIN')")
    @Operation(summary = "Create user", description = "Creates a new user (Admin only)")
    public ResponseEntity<Object> createUser(@Valid @RequestBody CreateUserDTO createUserDTO) {
        try {
            // Check if username already exists
            if (userService.getUserByUsername(createUserDTO.getUsername()).isPresent()) {
                return utils.createErrorResponse(
                        HttpStatus.CONFLICT,
                        "Username already exists: " + createUserDTO.getUsername()
                );
            }

            // Check if email already exists
            if (userService.getUserByEmail(createUserDTO.getEmail()).isPresent()) {
                return utils.createErrorResponse(
                        HttpStatus.CONFLICT,
                        "Email already exists: " + createUserDTO.getEmail()
                );
            }

            // Build the user DTO using UserDTO's built-in builder
            UserDTO userDTO = UserDTO.builder()
                    .username(createUserDTO.getUsername())
                    .email(createUserDTO.getEmail())
                    .firstName(createUserDTO.getFirstName())
                    .lastName(createUserDTO.getLastName())
                    .avatar(createUserDTO.getAvatar())
                    .sshPublicKey(createUserDTO.getSshPublicKey())
                    .roles(createUserDTO.getRoles())
                    .federationId(createUserDTO.getFederationId())
                    .withGeneratedAvatarIfEmpty()
                    .withNormalizedEmail()
                    .withUppercaseRoles()
                    .build();

            // Create user
            UserDTO createdUser = userService.createUser(userDTO, createUserDTO.getPassword());
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        } catch (IllegalArgumentException e) {
            // Handle validation errors
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, "Invalid input: " + e.getMessage());
        } catch (Exception e) {
            // Log the detailed error for debugging
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
    @PreAuthorize("hasRole('FEDERATION_ADMIN')")
    @Operation(summary = "Update user", description = "Updates an existing user (Admin only)")
    public ResponseEntity<Object> updateUser(
            @PathVariable String id,
            @Valid @RequestBody UpdateUserDTO updateUserDTO) {
        try {
            Optional<UserDTO> existingUserOpt = userService.getUserById(id);
            if (existingUserOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            UserDTO existingUser = existingUserOpt.get();

            // Build updated user using the from() method and builder
            UserDTO updatedUserDTO = UserDTO.from(existingUser)
                    .username(updateUserDTO.getUsername() != null ? updateUserDTO.getUsername() : existingUser.getUsername())
                    .firstName(updateUserDTO.getFirstName() != null ? updateUserDTO.getFirstName() : existingUser.getFirstName())
                    .lastName(updateUserDTO.getLastName() != null ? updateUserDTO.getLastName() : existingUser.getLastName())
                    .email(updateUserDTO.getEmail() != null ? updateUserDTO.getEmail() : existingUser.getEmail())
                    .avatar(updateUserDTO.getAvatar() != null ? updateUserDTO.getAvatar() : existingUser.getAvatar())
                    .sshPublicKey(updateUserDTO.getSshPublicKey() != null ? updateUserDTO.getSshPublicKey() : existingUser.getSshPublicKey())
                    .roles(updateUserDTO.getRoles() != null ? updateUserDTO.getRoles() : existingUser.getRoles())
                    .withNormalizedEmail()
                    .withUppercaseRoles()
                    .build();


            UserDTO updatedUser = userService.updateUser(id, updatedUserDTO, updateUserDTO.getPassword());
            return ResponseEntity.ok(updatedUser);
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
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update profile", description = "Updates the current user's profile")
    public ResponseEntity<Object> updateProfile(
            @Valid @RequestBody UpdateProfileDTO updateProfileDTO,
            Authentication authentication) {
        try {
            String keycloakId = utils.getCurrentUserKeycloakId(authentication);
            Optional<UserDTO> existingUserOpt = userService.getUserById(keycloakId);

            if (existingUserOpt.isEmpty()) {
                return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
            }

            UserDTO existingUser = existingUserOpt.get();

            // Validate SSH key if provided
            String sshPublicKey = updateProfileDTO.getSshPublicKey();
            if (sshPublicKey != null && !sshPublicKey.trim().isEmpty()) {
                sshPublicKey = sshKeyValidator.formatSshKey(sshPublicKey);
                if (!sshKeyValidator.isValidSshPublicKey(sshPublicKey)) {
                    return utils.createErrorResponse(HttpStatus.BAD_REQUEST,
                            "Invalid SSH public key format. Please provide a valid SSH key.");
                }
                updateProfileDTO.setSshPublicKey(sshPublicKey);
            }

            // Build updated user using the from() method and builder
            UserDTO updatedUserDTO = UserDTO.from(existingUser)
                    .firstName(updateProfileDTO.getFirstName() != null ? updateProfileDTO.getFirstName() : existingUser.getFirstName())
                    .lastName(updateProfileDTO.getLastName() != null ? updateProfileDTO.getLastName() : existingUser.getLastName())
                    .email(updateProfileDTO.getEmail() != null ? updateProfileDTO.getEmail() : existingUser.getEmail())
                    .avatar(updateProfileDTO.getAvatar() != null ? updateProfileDTO.getAvatar() : existingUser.getAvatar())
                    .sshPublicKey(updateProfileDTO.getSshPublicKey() != null ? updateProfileDTO.getSshPublicKey() : existingUser.getSshPublicKey())
                    .withNormalizedEmail()
                    .build();


            UserDTO updatedUser = userService.updateUser(keycloakId, updatedUserDTO, null);
            return ResponseEntity.ok(updatedUser);
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
    @PreAuthorize("hasRole('FEDERATION_ADMIN')")
    @Operation(summary = "Delete user", description = "Deletes an existing user (Admin only)")
    public ResponseEntity<Object> deleteUser(@PathVariable String id, Authentication authentication) {
        try {
            Optional<UserDTO> userOpt = userService.getUserById(id);
            if (userOpt.isEmpty()) {
                return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
            }
            
            String currentKeycloakId = utils.getCurrentUserKeycloakId(authentication);
            boolean deleted = userService.deleteUser(id, currentKeycloakId);
            
            if (!deleted) {
                return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to delete user");
            }

            return utils.createSuccessResponse("User deleted successfully");
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
    @PreAuthorize("hasRole('FEDERATION_ADMIN')")
    @Operation(summary = "Get users by role", description = "Retrieves users with a specific role (Admin only)")
    public ResponseEntity<List<UserDTO>> getUsersByRole(@PathVariable String role) {
        List<UserDTO> users = userService.getUsersByRole(role.toUpperCase());
        return ResponseEntity.ok(users);
    }

    /**
     * Update current user's SSH key
     *
     * @param sshKeyDTO The SSH key data
     * @param authentication User authentication object
     * @return The updated user or error response
     */
    @PutMapping("/me/ssh-key")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update SSH key", description = "Updates the current user's SSH public key")
    public ResponseEntity<Object> updateSshKey(
            @Valid @RequestBody SshKeyDTO sshKeyDTO,
            Authentication authentication) {
        try {
            String keycloakId = utils.getCurrentUserKeycloakId(authentication);

            Optional<UserDTO> existingUserOpt = userService.getUserById(keycloakId);
            if (existingUserOpt.isEmpty()) {
                return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
            }

            // Validate SSH key
            String sshPublicKey = sshKeyDTO.getSshPublicKey();
            if (sshPublicKey != null && !sshPublicKey.trim().isEmpty()) {
                sshPublicKey = sshKeyValidator.formatSshKey(sshPublicKey);
                if (!sshKeyValidator.isValidSshPublicKey(sshPublicKey)) {
                    return utils.createErrorResponse(HttpStatus.BAD_REQUEST,
                            "Invalid SSH public key format. Please provide a valid SSH key.");
                }
            }

            UserDTO updatedUser = userService.updateUserSshKey(keycloakId, sshPublicKey);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, "Failed to update SSH key: " + e.getMessage());
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
}