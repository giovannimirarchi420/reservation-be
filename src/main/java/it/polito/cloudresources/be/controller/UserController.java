package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.UserDTO;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.service.UserService;
import it.polito.cloudresources.be.util.ControllerUtils;
import it.polito.cloudresources.be.util.SshKeyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST API controller for managing users (now fully integrated with Keycloak)
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "API for managing users")
@SecurityRequirement(name = "bearer-auth")
public class UserController {

    private final UserService userService;
    private final KeycloakService keycloakService;
    private final ControllerUtils utils;
    private final SshKeyValidator sshKeyValidator;

    /**
     * Get all users (admin only)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all users", description = "Retrieves all users (Admin only)")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Get user by ID (admin only)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get user by ID", description = "Retrieves a specific user by their ID (Admin only)")
    public ResponseEntity<UserDTO> getUserById(@PathVariable String id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get current user's profile
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
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create user", description = "Creates a new user (Admin only)")
    public ResponseEntity<Object> createUser(@RequestBody Map<String, Object> userData) {
        try {
            // Extract basic data to check
            String username = (String) userData.get("username");
            String email = (String) userData.get("email");
            String password = (String) userData.get("password");
            
            // Check if username already exists
            if (userService.getUserByUsername(username).isPresent()) {
                return utils.createErrorResponse(
                    HttpStatus.CONFLICT, 
                    "Username already exists: " + username
                );
            }
            
            // Check if email already exists
            if (userService.getUserByEmail(email).isPresent()) {
                return utils.createErrorResponse(
                    HttpStatus.CONFLICT, 
                    "Email already exists: " + email
                );
            }
            
            // Build the user DTO
            UserDTO userDTO = new UserDTO();
            userDTO.setUsername(username);
            userDTO.setEmail(email);
            userDTO.setFirstName((String) userData.get("firstName"));
            userDTO.setLastName((String) userData.get("lastName"));
            
            // Set avatar
            if (userData.containsKey("avatar")) {
                userDTO.setAvatar((String) userData.get("avatar"));
            } else {
                userDTO.setAvatar(generateAvatarFromName(userDTO.getFirstName(), userDTO.getLastName()));
            }
            
            // Set roles
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) userData.get("roles");
            if (roles != null) {
                userDTO.setRoles(roles.stream().map(String::toUpperCase).collect(Collectors.toSet()));
            }
            
            // Create user
            UserDTO createdUser = userService.createUser(userDTO, password);
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
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user", description = "Updates an existing user (Admin only)")
    public ResponseEntity<Object> updateUser(@PathVariable String id, @RequestBody Map<String, Object> userData) {
        try {
            Optional<UserDTO> existingUserOpt = userService.getUserById(id);
            if (existingUserOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            UserDTO existingUser = existingUserOpt.get();
            UserDTO updatedUserDTO = buildUserDTOForUpdate(userData, existingUser);
            
            UserDTO updatedUser = userService.updateUser(id, updatedUserDTO);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, "Failed to update user: " + e.getMessage());
        }
    }
    
    /**
     * Update current user's profile
     */
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update profile", description = "Updates the current user's profile")
    public ResponseEntity<Object> updateProfile(@RequestBody Map<String, Object> userData, Authentication authentication) {
        try {
            String keycloakId = utils.getCurrentUserKeycloakId(authentication);
            Optional<UserDTO> existingUserOpt = userService.getUserById(keycloakId);
            
            if (existingUserOpt.isEmpty()) {
                return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
            }
            
            // Validate SSH key if provided
            if (userData.containsKey("sshPublicKey")) {
                String sshPublicKey = (String) userData.get("sshPublicKey");
                if (sshPublicKey != null && !sshPublicKey.trim().isEmpty()) {
                    sshPublicKey = sshKeyValidator.formatSshKey(sshPublicKey);
                    if (!sshKeyValidator.isValidSshPublicKey(sshPublicKey)) {
                        return utils.createErrorResponse(HttpStatus.BAD_REQUEST, 
                            "Invalid SSH public key format. Please provide a valid SSH key.");
                    }
                    userData.put("sshPublicKey", sshPublicKey);
                }
            }
            
            UserDTO existingUser = existingUserOpt.get();
            UserDTO updatedUserDTO = buildUserDTOForProfileUpdate(userData, existingUser);
            
            UserDTO updatedUser = userService.updateUser(keycloakId, updatedUserDTO);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return utils.createErrorResponse(HttpStatus.BAD_REQUEST, "Failed to update profile: " + e.getMessage());
        }
    }
    
    /**
     * Delete user (admin only)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete user", description = "Deletes an existing user (Admin only)")
    public ResponseEntity<Object> deleteUser(@PathVariable String id) {
        try {
            Optional<UserDTO> userOpt = userService.getUserById(id);
            if (userOpt.isEmpty()) {
                return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
            }
            
            boolean deleted = userService.deleteUser(id);
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
     */
    @GetMapping("/by-role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get users by role", description = "Retrieves users with a specific role (Admin only)")
    public ResponseEntity<List<UserDTO>> getUsersByRole(@PathVariable String role) {
        List<UserDTO> users = userService.getUsersByRole(role.toUpperCase());
        return ResponseEntity.ok(users);
    }

    /**
     * Update current user's SSH key
     */
    @PutMapping("/me/ssh-key")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update SSH key", description = "Updates the current user's SSH public key")
    public ResponseEntity<Object> updateSshKey(@RequestBody Map<String, String> request, Authentication authentication) {
        try {
            String sshPublicKey = request.get("sshPublicKey");
            String keycloakId = utils.getCurrentUserKeycloakId(authentication);
            
            Optional<UserDTO> existingUserOpt = userService.getUserById(keycloakId);
            if (existingUserOpt.isEmpty()) {
                return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
            }
            
            // Validate SSH key
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
     */
    @GetMapping("/me/ssh-key")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get SSH key", description = "Retrieves the current user's SSH public key")
    public ResponseEntity<Object> getSshKey(Authentication authentication) {
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
        
        Optional<String> sshKey = userService.getUserSshKey(keycloakId);
        
        Map<String, String> response = new HashMap<>();
        response.put("sshPublicKey", sshKey.orElse(null));
        
        return ResponseEntity.ok(response);
    }

    /**
     * Delete current user's SSH key
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
    
    // Helper methods
    
    private UserDTO buildUserDTOForUpdate(Map<String, Object> userData, UserDTO existingUser) {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(existingUser.getId());
        
        userDTO.setUsername(getStringOrDefault(userData, "username", existingUser.getUsername()));
        userDTO.setFirstName(getStringOrDefault(userData, "firstName", existingUser.getFirstName()));
        userDTO.setLastName(getStringOrDefault(userData, "lastName", existingUser.getLastName()));
        userDTO.setEmail(getStringOrDefault(userData, "email", existingUser.getEmail()));
        userDTO.setAvatar(getStringOrDefault(userData, "avatar", existingUser.getAvatar()));
        userDTO.setSshPublicKey(getStringOrDefault(userData, "sshPublicKey", existingUser.getSshPublicKey()));
        
        if (userData.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) userData.get("roles");
            userDTO.setRoles(roles.stream().map(String::toUpperCase).collect(Collectors.toSet()));
        } else {
            userDTO.setRoles(existingUser.getRoles());
        }
        
        return userDTO;
    }
    
    private UserDTO buildUserDTOForProfileUpdate(Map<String, Object> userData, UserDTO existingUser) {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(existingUser.getId());
        userDTO.setUsername(existingUser.getUsername()); // Username can't be changed by the user
        
        userDTO.setFirstName(getStringOrDefault(userData, "firstName", existingUser.getFirstName()));
        userDTO.setLastName(getStringOrDefault(userData, "lastName", existingUser.getLastName()));
        userDTO.setEmail(getStringOrDefault(userData, "email", existingUser.getEmail()));
        userDTO.setAvatar(getStringOrDefault(userData, "avatar", existingUser.getAvatar()));
        userDTO.setSshPublicKey(getStringOrDefault(userData, "sshPublicKey", existingUser.getSshPublicKey()));
        userDTO.setRoles(existingUser.getRoles()); // Users cannot change their own roles
        
        return userDTO;
    }
    
    private String generateAvatarFromName(String firstName, String lastName) {
        StringBuilder avatar = new StringBuilder();
        
        if (firstName != null && !firstName.isEmpty()) {
            avatar.append(firstName.substring(0, 1).toUpperCase());
        }
        
        if (lastName != null && !lastName.isEmpty()) {
            avatar.append(lastName.substring(0, 1).toUpperCase());
        }
        
        return avatar.toString();
    }
    
    private String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        return map.containsKey(key) ? (String) map.get(key) : defaultValue;
    }
}