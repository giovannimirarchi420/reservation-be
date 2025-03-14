package it.polito.cloudresources.be.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.polito.cloudresources.be.dto.UserDTO;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.service.UserService;
import it.polito.cloudresources.be.util.ControllerUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ControllerUtils utils;

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
        String keycloakId = utils.getCurrentUserKeycloakId(authentication);
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
    public ResponseEntity<Object> createUser(@RequestBody Map<String, Object> userData) {
        try {
            // Extract basic data to check
            String username = (String) userData.get("username");
            String email = (String) userData.get("email");
            
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
            
            // Check with Keycloak as well (double check)
            if (keycloakService.getUserByUsername(username).isPresent()) {
                return utils.createErrorResponse(
                    HttpStatus.CONFLICT, 
                    "Username already exists in authentication system: " + username
                );
            }
            
            if (keycloakService.getUserByEmail(email).isPresent()) {
                return utils.createErrorResponse(
                    HttpStatus.CONFLICT, 
                    "Email already exists in authentication system: " + email
                );
            }
            
            String keycloakId = createUserInKeycloak(userData);
            if (keycloakId == null) {
                return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create user in Keycloak");
            }
            
            UserDTO userDTO = buildUserDTOFromData(userData, keycloakId);
            UserDTO createdUser = userService.createUser(userDTO);
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
    public ResponseEntity<Object> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> userData) {
        try {
            Optional<UserDTO> existingUserOpt = userService.getUserById(id);
            if (existingUserOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            UserDTO existingUser = existingUserOpt.get();
            boolean keycloakUpdated = keycloakService.updateUser(existingUser.getKeycloakId(), userData);
            if (!keycloakUpdated) {
                return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update user in Keycloak");
            }
            
            UserDTO userDTO = buildUserDTOForUpdate(id, userData, existingUser);
            UserDTO updatedUser = userService.updateUser(id, userDTO);
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
            Optional<UserDTO> existingUserOpt = userService.getUserByKeycloakId(keycloakId);
            
            if (existingUserOpt.isEmpty()) {
                return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
            }
            
            UserDTO existingUser = existingUserOpt.get();
            Map<String, Object> keycloakUpdate = extractKeycloakUpdateFields(userData);
            
            if (!keycloakUpdate.isEmpty() && !keycloakService.updateUser(keycloakId, keycloakUpdate)) {
                return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update user in Keycloak");
            }
            
            UserDTO userDTO = buildUserDTOForProfileUpdate(userData, existingUser);
            UserDTO updatedUser = userService.updateUser(existingUser.getId(), userDTO);
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
    public ResponseEntity<Object> deleteUser(@PathVariable Long id) {
        try {
            Optional<UserDTO> userOpt = userService.getUserById(id);
            if (userOpt.isEmpty()) {
                return utils.createErrorResponse(HttpStatus.NOT_FOUND, "User not found");
            }
            
            UserDTO user = userOpt.get();
            if (!keycloakService.deleteUser(user.getKeycloakId())) {
                return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                        "Failed to delete user from Keycloak");
            }
            
            boolean deleted = userService.deleteUser(id);
            if (!deleted) {
                return utils.createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                        "Failed to delete user from database");
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
    
    // Helper methods
    
    private String createUserInKeycloak(Map<String, Object> userData) {
        // Validate required fields
        String username = (String) userData.get("username");
        String email = (String) userData.get("email");
        String firstName = (String) userData.get("firstName");
        String lastName = (String) userData.get("lastName");
        String password = (String) userData.get("password");
        
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required");
        }
        
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email is required");
        }
        
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }
        
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is required");
        }
        
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }
        
        // Get roles, with validation
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) userData.get("roles");
        
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role must be specified");
        }
        
        // Attempt to create the user in Keycloak
        String keycloakId = keycloakService.createUser(username, email, firstName, lastName, password, roles);
        
        if (keycloakId == null) {
            throw new RuntimeException("Failed to create user in authentication system. This could be due to an existing username or email, invalid role, or a system error.");
        }
        
        return keycloakId;
    }
    
    private UserDTO buildUserDTOFromData(Map<String, Object> userData, String keycloakId) {
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername((String) userData.get("username"));
        userDTO.setFirstName((String) userData.get("firstName"));
        userDTO.setLastName((String) userData.get("lastName"));
        userDTO.setEmail((String) userData.get("email"));
        userDTO.setKeycloakId(keycloakId);
        
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) userData.get("roles");
        userDTO.setRoles(roles.stream().map(String::toUpperCase).collect(Collectors.toSet()));
        
        // Set avatar
        if (userData.containsKey("avatar")) {
            userDTO.setAvatar((String) userData.get("avatar"));
        } else {
            userDTO.setAvatar(generateAvatarFromName(userDTO.getFirstName(), userDTO.getLastName()));
        }
        
        return userDTO;
    }
    
    private UserDTO buildUserDTOForUpdate(Long id, Map<String, Object> userData, UserDTO existingUser) {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(id);
        userDTO.setKeycloakId(existingUser.getKeycloakId());
        
        userDTO.setUsername(getStringOrDefault(userData, "username", existingUser.getUsername()));
        userDTO.setFirstName(getStringOrDefault(userData, "firstName", existingUser.getFirstName()));
        userDTO.setLastName(getStringOrDefault(userData, "lastName", existingUser.getLastName()));
        userDTO.setEmail(getStringOrDefault(userData, "email", existingUser.getEmail()));
        userDTO.setAvatar(getStringOrDefault(userData, "avatar", existingUser.getAvatar()));
        
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
        userDTO.setKeycloakId(existingUser.getKeycloakId());
        userDTO.setUsername(existingUser.getUsername()); // Username can't be changed by the user
        
        userDTO.setFirstName(getStringOrDefault(userData, "firstName", existingUser.getFirstName()));
        userDTO.setLastName(getStringOrDefault(userData, "lastName", existingUser.getLastName()));
        userDTO.setEmail(getStringOrDefault(userData, "email", existingUser.getEmail()));
        userDTO.setAvatar(getStringOrDefault(userData, "avatar", existingUser.getAvatar()));
        userDTO.setRoles(existingUser.getRoles()); // Users cannot change their own roles
        
        return userDTO;
    }
    
    private Map<String, Object> extractKeycloakUpdateFields(Map<String, Object> userData) {
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
        
        return keycloakUpdate;
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