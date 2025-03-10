package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.UserDTO;
import it.polito.cloudresources.be.model.User;
import it.polito.cloudresources.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for user operations
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    /**
     * Get all users
     */
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get user by ID
     */
    public Optional<UserDTO> getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::mapToDTO);
    }

    /**
     * Get user by email
     */
    public Optional<UserDTO> getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(this::mapToDTO);
    }
    
    /**
     * Get user by username
     */
    public Optional<UserDTO> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::mapToDTO);
    }

    /**
     * Get user by Keycloak ID
     */
    public Optional<UserDTO> getUserByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .map(this::mapToDTO);
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
        User user = mapToEntity(userDTO);
        User savedUser = userRepository.save(user);
        return mapToDTO(savedUser);
    }

    /**
     * Update existing user
     */
    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        updateUserFields(existingUser, userDTO);
        User updatedUser = userRepository.save(existingUser);
        return mapToDTO(updatedUser);
    }

    /**
     * Delete user
     */
    @Transactional
    public boolean deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        
        userRepository.deleteById(id);
        return true;
    }

    /**
     * Get users by role
     */
    public List<UserDTO> getUsersByRole(String role) {
        return userRepository.findByRolesContaining(role).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    
    // Private helper methods
    
    private void updateUserFields(User user, UserDTO userDTO) {
        if (userDTO.getUsername() != null) {
            user.setUsername(userDTO.getUsername());
        }
        if (userDTO.getFirstName() != null) {
            user.setFirstName(userDTO.getFirstName());
        }
        if (userDTO.getLastName() != null) {
            user.setLastName(userDTO.getLastName());
        }
        if (userDTO.getEmail() != null) {
            user.setEmail(userDTO.getEmail());
        }
        if (userDTO.getAvatar() != null) {
            user.setAvatar(userDTO.getAvatar());
        }
        
        // Update roles if provided
        if (userDTO.getRoles() != null) {
            user.setRoles(userDTO.getRoles());
        }
    }
    
    private UserDTO mapToDTO(User user) {
        return modelMapper.map(user, UserDTO.class);
    }
    
    private User mapToEntity(UserDTO dto) {
        return modelMapper.map(dto, User.class);
    }
}