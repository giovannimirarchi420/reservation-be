package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.ApiResponseDTO;
import it.polito.cloudresources.be.dto.UserDTO;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API controller for managing users
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "API for managing users")
@SecurityRequirement(name = "bearer-auth")
public class UserController {

    private final UserService userService;
    private final KeycloakService keycloakService;

    /**
     * Get current user's Keycloak ID from JWT token
     */
    private String getCurrentUserKeycloakId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            return jwtAuth.getToken().getSubject();
        }
        return null;
    }

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
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
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
        String keycloakId = getCurrentUserKeycloakId(authentication);
        return userService.getUserByKeycloakId(keycloakId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new user (admin only)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create user", description = "Creates a new user (Admin only)")
    public ResponseEntity<Object> createUser(@Valid @RequestBody Map<String, Object> userData) {
        try {
            // Extract user details
            String username = (String) userData.get("username");
            String email = (String) userData.get("email");
            String firstName = (String) userData.get("firstName");
            String lastName = (String) userData.get("lastName");
            String password = (String) userData.get("password");
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) userData.get("roles");


            // Create user in Keycloak
            String keycloakId = keycloakService.createUser(username, email, firstName, lastName, password, roles);
            
            if (keycloakId == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ApiResponseDTO(false, "Failed to create user in Keycloak"));
            }
            
            // Create user in our database
            UserDTO userDTO = new UserDTO();
            userDTO.setUsername(username);
            userDTO.setFirstName(firstName);
            userDTO.setLastName(lastName);
            userDTO.setEmail(email);
            userDTO.setKeycloakId(keycloakId);
            userDTO.setRoles(roles.stream().map(String::toUpperCase).collect(Collectors.toSet()));
            
            if (userData.containsKey("avatar")) {
                userDTO.setAvatar((String) userData.get("avatar"));
            } else {
                // Create initials avatar
                String avatar = "";
                if (firstName != null && !firstName.isEmpty()) {
                    avatar += firstName.substring(0, 1).toUpperCase();
                }
                if (lastName != null && !lastName.isEmpty()) {
                    avatar += lastName.substring(0, 1).toUpperCase();
                }
                userDTO.setAvatar(avatar);
            }
            
            UserDTO createdUser = userService.createUser(userDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponseDTO(false, "Failed to create user: " + e.getMessage()));
        }
    }

    /**
     * Update an existing user (admin only)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user", description = "Updates an existing user (Admin only)")
    public ResponseEntity<Object> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody Map<String, Object> userData) {
        
        try {
            // Get the existing user
            UserDTO existingUser = userService.getUserById(id).orElse(null);
            if (existingUser == null) {
                return ResponseEntity.notFound().build();
            }
            
            // Update user in Keycloak
            boolean keycloakUpdated = keycloakService.updateUser(existingUser.getKeycloakId(), userData);
            
            if (!keycloakUpdated) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ApiResponseDTO(false, "Failed to update user in Keycloak"));
            }
            
            // Update user in our database
            UserDTO userDTO = new UserDTO();
            userDTO.setId(id);
            userDTO.setKeycloakId(existingUser.getKeycloakId());
            
            if (userData.containsKey("username")) {
                userDTO.setUsername((String) userData.get("username"));
            } else {
                userDTO.setUsername(existingUser.getUsername());
            }
            
            if (userData.containsKey("firstName")) {
                userDTO.setFirstName((String) userData.get("firstName"));
            } else {
                userDTO.setFirstName(existingUser.getFirstName());
            }
            
            if (userData.containsKey("lastName")) {
                userDTO.setLastName((String) userData.get("lastName"));
            } else {
                userDTO.setLastName(existingUser.getLastName());
            }
            
            if (userData.containsKey("email")) {
                userDTO.setEmail((String) userData.get("email"));
            } else {
                userDTO.setEmail(existingUser.getEmail());
            }
            
            if (userData.containsKey("avatar")) {
                userDTO.setAvatar((String) userData.get("avatar"));
            } else {
                userDTO.setAvatar(existingUser.getAvatar());
            }
            
            if (userData.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) userData.get("roles");
                userDTO.setRoles(roles.stream().map(String::toUpperCase).collect(Collectors.toSet()));
            } else {
                userDTO.setRoles(existingUser.getRoles());
            }
            
            UserDTO updatedUser = userService.updateUser(id, userDTO);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponseDTO(false, "Failed to update user: " + e.getMessage()));
        }
    }
    
    /**
     * Update current user's profile
     */
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update profile", description = "Updates the current user's profile")
    public ResponseEntity<Object> updateProfile(
            @Valid @RequestBody Map<String, Object> userData,
            Authentication authentication) {
        
        try {
            String keycloakId = getCurrentUserKeycloakId(authentication);
            UserDTO existingUser = userService.getUserByKeycloakId(keycloakId).orElse(null);
            
            if (existingUser == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponseDTO(false, "User not found"));
            }
            
            // Restrict which fields can be updated by the user themselves
            Map<String, Object> keycloakUpdate = new java.util.HashMap<>();
            
            if (userData.containsKey("firstName")) {
                keycloakUpdate.put("firstName", userData.get("firstName"));
            }
            
            if (userData.containsKey("lastName")) {
                keycloakUpdate.put("lastName", userData.get("lastName"));
            }
            
            if (userData.containsKey("email")) {
                keycloakUpdate.put("email", userData.get("email"));
            }
            
            if (userData.containsKey("password")) {
                keycloakUpdate.put("password", userData.get("password"));
            }
            
            // Update user in Keycloak if there are changes
            if (!keycloakUpdate.isEmpty()) {
                boolean keycloakUpdated = keycloakService.updateUser(keycloakId, keycloakUpdate);
                
                if (!keycloakUpdated) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(new ApiResponseDTO(false, "Failed to update user in Keycloak"));
                }
            }
            
            // Update user in our database
            UserDTO userDTO = new UserDTO();
            userDTO.setId(existingUser.getId());
            userDTO.setKeycloakId(keycloakId);
            
            // Username can't be changed by the user
            userDTO.setUsername(existingUser.getUsername());
            
            if (userData.containsKey("firstName")) {
                userDTO.setFirstName((String) userData.get("firstName"));
            } else {
                userDTO.setFirstName(existingUser.getFirstName());
            }
            
            if (userData.containsKey("lastName")) {
                userDTO.setLastName((String) userData.get("lastName"));
            } else {
                userDTO.setLastName(existingUser.getLastName());
            }
            
            if (userData.containsKey("email")) {
                userDTO.setEmail((String) userData.get("email"));
            } else {
                userDTO.setEmail(existingUser.getEmail());
            }
            
            if (userData.containsKey("avatar")) {
                userDTO.setAvatar((String) userData.get("avatar"));
            } else {
                userDTO.setAvatar(existingUser.getAvatar());
            }
            
            // Users cannot change their own roles
            userDTO.setRoles(existingUser.getRoles());
            
            UserDTO updatedUser = userService.updateUser(existingUser.getId(), userDTO);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponseDTO(false, "Failed to update profile: " + e.getMessage()));
        }
    }

    /**
     * Delete user (admin only)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete user", description = "Deletes an existing user (Admin only)")
    public ResponseEntity<ApiResponseDTO> deleteUser(@PathVariable Long id) {
        try {
            UserDTO user = userService.getUserById(id).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponseDTO(false, "User not found"));
            }
            
            // Delete from Keycloak
            boolean keycloakDeleted = keycloakService.deleteUser(user.getKeycloakId());
            
            if (!keycloakDeleted) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ApiResponseDTO(false, "Failed to delete user from Keycloak"));
            }
            
            // Delete from our database
            boolean deleted = userService.deleteUser(id);
            
            if (deleted) {
                return ResponseEntity.ok(new ApiResponseDTO(true, "User deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ApiResponseDTO(false, "Failed to delete user from database"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponseDTO(false, "Failed to delete user: " + e.getMessage()));
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
}