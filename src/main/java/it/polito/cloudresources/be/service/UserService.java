package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.UserDTO;
import it.polito.cloudresources.be.mapper.UserMapper;
import it.polito.cloudresources.be.model.User;
import it.polito.cloudresources.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for user operations
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    /**
     * Get all users
     */
    public List<UserDTO> getAllUsers() {
        return userMapper.toDto(userRepository.findAll());
    }

    /**
     * Get user by ID
     */
    public Optional<UserDTO> getUserById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toDto);
    }

    /**
     * Get user by email
     */
    public Optional<UserDTO> getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(userMapper::toDto);
    }
    
    /**
     * Get user by username
     */
    public Optional<UserDTO> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(userMapper::toDto);
    }

    /**
     * Get user by Keycloak ID
     */
    public Optional<UserDTO> getUserByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .map(userMapper::toDto);
    }

    /**
     * Get user ID by Keycloak ID
     */
    public Long getUserIdByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .map(User::getId)
                .orElse(null);
    }

    /**
     * Create new user
     */
    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        User user = userMapper.toEntity(userDTO);
        User savedUser = userRepository.save(user);
        
        // Log the action
        auditLogService.logAdminAction("User", "create", 
                "Created user: " + savedUser.getUsername());
                
        return userMapper.toDto(savedUser);
    }

    /**
     * Update existing user
     */
    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Apply updates but preserve ID
        User updatedUser = userMapper.toEntity(userDTO);
        updatedUser.setId(id);
        
        // Preserve related entities that aren't in the DTO
        updatedUser.setEvents(existingUser.getEvents());
        
        User savedUser = userRepository.save(updatedUser);
        
        // Log the action
        auditLogService.logAdminAction("User", "update", 
                "Updated user: " + savedUser.getUsername());
                
        return userMapper.toDto(savedUser);
    }

    /**
     * Delete user
     */
    @Transactional
    public boolean deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        
        // Get username for logging before deletion
        String username = userRepository.findById(id)
                .map(User::getUsername)
                .orElse("Unknown");
        
        userRepository.deleteById(id);
        
        // Log the action
        auditLogService.logAdminAction("User", "delete", 
                "Deleted user: " + username);
        
        return true;
    }

    /**
     * Get users by role
     */
    public List<UserDTO> getUsersByRole(String role) {
        return userMapper.toDto(userRepository.findByRolesContaining(role));
    }
}