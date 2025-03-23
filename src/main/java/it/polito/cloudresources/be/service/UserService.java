package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.UserDTO;
import it.polito.cloudresources.be.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.*;
/**
 * Service for user operations, now using Keycloak as the source of truth
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final KeycloakService keycloakService;
    private final AuditLogService auditLogService;
    private final UserMapper userMapper;

    // UserMapper è ora iniettato per gestire la conversione da UserRepresentation a UserDTO

    /**
     * Get all users
     */
    public List<UserDTO> getAllUsers() {
        return userMapper.toDto(keycloakService.getUsers());
    }

    /**
     * Get user by ID (Keycloak ID)
     */
    public Optional<UserDTO> getUserById(String id) {
        return keycloakService.getUserById(id)
                .map(userMapper::toDto);
    }

    /**
     * Get user by email
     */
    public Optional<UserDTO> getUserByEmail(String email) {
        return keycloakService.getUserByEmail(email)
                .map(userMapper::toDto);
    }
    
    /**
     * Get user by username
     */
    public Optional<UserDTO> getUserByUsername(String username) {
        return keycloakService.getUserByUsername(username)
                .map(userMapper::toDto);
    }

    /**
     * Create new user
     */
    public UserDTO createUser(UserDTO userDTO, String password) {
        // Collect roles
        List<String> rolesList = userDTO.getRoles() != null ? 
                new ArrayList<>(userDTO.getRoles()) : new ArrayList<>();
                
        // Create user in Keycloak
        String userId = keycloakService.createUser(
                userDTO.getUsername(),
                userDTO.getEmail(),
                userDTO.getFirstName(),
                userDTO.getLastName(),
                password,
                rolesList,
                userDTO.getSshPublicKey(),
                userDTO.getAvatar()
        );
        
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
     * Update existing user
     */
    public UserDTO updateUser(String id, UserDTO userDTO) {
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
        
        boolean updated = keycloakService.updateUser(id, attributes);
        if (!updated) {
            throw new RuntimeException("Failed to update user in Keycloak");
        }
        
        // Log the action
        auditLogService.logAdminAction("User", "update", 
                "Updated user: " + userDTO.getUsername());
                
        // Retrieve and return the updated user
        return keycloakService.getUserById(id)
                .map(userMapper::toDto)
                .orElseThrow(() -> new RuntimeException("User updated but could not be retrieved"));
    }

    /**
     * Delete user
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
     */
    public List<UserDTO> getUsersByRole(String role) {
        return userMapper.toDto(keycloakService.getUsersByRole(role));
    }
    
    /**
     * Update user SSH key
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
     */
    public Optional<String> getUserSshKey(String id) {
        return keycloakService.getUserSshKey(id);
    }
    
    /**
     * Delete user SSH key
     */
    public boolean deleteUserSshKey(String id) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(KeycloakService.ATTR_SSH_KEY, null); // Set to null to remove
        
        return keycloakService.updateUser(id, attributes);
    }
}