package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.users.UserDTO;
import it.polito.cloudresources.be.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.*;
/**
 * Service for user operations, using Keycloak as the source of truth
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final KeycloakService keycloakService;
    private final AuditLogService auditLogService;
    private final UserMapper userMapper;

    /**
     * Get all users
     * @return A list of all users
     */
    public List<UserDTO> getAllUsers() {
        return userMapper.toDto(keycloakService.getUsers());
    }

    /**
     * Get user by ID (Keycloak ID)
     * @param id The Keycloak user ID
     * @return Optional containing the user if found
     */
    public Optional<UserDTO> getUserById(String id) {
        return keycloakService.getUserById(id)
                .map(userMapper::toDto);
    }

    /**
     * Get user by email
     * @param email The email to search for
     * @return Optional containing the user if found
     */
    public Optional<UserDTO> getUserByEmail(String email) {
        return keycloakService.getUserByEmail(email)
                .map(userMapper::toDto);
    }

    /**
     * Get user by username
     * @param username The username to search for
     * @return Optional containing the user if found
     */
    public Optional<UserDTO> getUserByUsername(String username) {
        return keycloakService.getUserByUsername(username)
                .map(userMapper::toDto);
    }

    /**
     * Create new user
     * @param userDTO The user data transfer object containing the information for the new user
     * @param password The password for the new user
     * @return The created user
     */
    public UserDTO createUser(UserDTO userDTO, String password) {
        // Collect roles
        List<String> rolesList = userDTO.getRoles() != null ?
                new ArrayList<>(userDTO.getRoles()) : new ArrayList<>();

        // Create user in Keycloak
        String userId = keycloakService.createUser(userDTO, password);

        if (userId == null) {
            throw new RuntimeException("Failed to create user in Keycloak");
        }

        // Log the action
        auditLogService.logAdminAction("User", "create",
                "Created user: " + userDTO.getUsername());

        // Retrieve and return the newly created user
        return keycloakService.getUserById(userId)
                .map(userMapper::toDto)
                .orElseThrow(() -> new RuntimeException("User created but could not be retrieved"));
    }

    /**
     * Update existing user with optional password update
     * @param id The Keycloak user ID
     * @param userDTO The user data to update
     * @param password Optional new password (null to leave unchanged)
     * @return The updated user
     */
    public UserDTO updateUser(String id, UserDTO userDTO, String password) {
        Map<String, Object> attributes = new HashMap<>();

        // Only update fields that are present in the DTO
        if (userDTO.getUsername() != null) {
            attributes.put("username", userDTO.getUsername());
        }

        if (userDTO.getEmail() != null) {
            attributes.put("email", userDTO.getEmail());
        }

        if (userDTO.getFirstName() != null) {
            attributes.put("firstName", userDTO.getFirstName());
        }

        if (userDTO.getLastName() != null) {
            attributes.put("lastName", userDTO.getLastName());
        }

        if (userDTO.getAvatar() != null) {
            attributes.put(KeycloakService.ATTR_AVATAR, userDTO.getAvatar());
        }

        if (userDTO.getSshPublicKey() != null) {
            attributes.put(KeycloakService.ATTR_SSH_KEY, userDTO.getSshPublicKey());
        }

        if (userDTO.getRoles() != null) {
            attributes.put("roles", new ArrayList<>(userDTO.getRoles()));
        }

        // Add password to attributes if provided
        if (password != null && !password.isEmpty()) {
            attributes.put("password", password);
        }

        boolean updated = keycloakService.updateUser(id, attributes);
        if (!updated) {
            throw new RuntimeException("Failed to update user in Keycloak");
        }

        // Log the action
        auditLogService.logAdminAction("User", "update",
                "Updated user with ID: " + id);

        // Retrieve and return the updated user
        return keycloakService.getUserById(id)
                .map(userMapper::toDto)
                .orElseThrow(() -> new RuntimeException("User updated but could not be retrieved"));
    }

    /**
     * Delete user
     * @param id The Keycloak user ID to delete
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteUser(String id) {
        // Get username for logging before deletion
        String username = keycloakService.getUserById(id)
                .map(UserRepresentation::getUsername)
                .orElse("Unknown");

        boolean deleted = keycloakService.deleteUser(id);

        if (deleted) {
            // Log the action
            auditLogService.logAdminAction("User", "delete",
                    "Deleted user: " + username);
        }

        return deleted;
    }

    /**
     * Get users by role
     * @param role The role to search for
     * @return List of users with the specified role
     */
    public List<UserDTO> getUsersByRole(String role) {
        return userMapper.toDto(keycloakService.getUsersByRole(role));
    }

    /**
     * Update user SSH key
     * @param id The Keycloak user ID
     * @param sshKey The new SSH key
     * @return The updated user
     */
    public UserDTO updateUserSshKey(String id, String sshKey) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(KeycloakService.ATTR_SSH_KEY, sshKey);

        boolean updated = keycloakService.updateUser(id, attributes);
        if (!updated) {
            throw new RuntimeException("Failed to update user SSH key in Keycloak");
        }

        // Retrieve and return the updated user
        return keycloakService.getUserById(id)
                .map(userMapper::toDto)
                .orElseThrow(() -> new RuntimeException("User SSH key updated but user could not be retrieved"));
    }

    /**
     * Get user SSH key
     * @param id The Keycloak user ID
     * @return Optional containing the SSH key if found
     */
    public Optional<String> getUserSshKey(String id) {
        return keycloakService.getUserSshKey(id);
    }

    /**
     * Delete user SSH key
     * @param id The Keycloak user ID
     * @return true if deleted successfully, false otherwise
     */
    public boolean deleteUserSshKey(String id) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(KeycloakService.ATTR_SSH_KEY, null); // Set to null to remove

        return keycloakService.updateUser(id, attributes);
    }
}