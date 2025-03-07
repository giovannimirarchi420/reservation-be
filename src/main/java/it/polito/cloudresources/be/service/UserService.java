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
    private final KeycloakService keycloakService;

    /**
     * Get all users
     */
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> modelMapper.map(user, UserDTO.class))
                .collect(Collectors.toList());
    }

    /**
     * Get user by ID
     */
    public Optional<UserDTO> getUserById(Long id) {
        return userRepository.findById(id)
                .map(user -> modelMapper.map(user, UserDTO.class));
    }

    /**
     * Get user by email
     */
    public Optional<UserDTO> getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(user -> modelMapper.map(user, UserDTO.class));
    }

    /**
     * Get user by Keycloak ID
     */
    public Optional<UserDTO> getUserByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
                .map(user -> modelMapper.map(user, UserDTO.class));
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
        User user = modelMapper.map(userDTO, User.class);
        User savedUser = userRepository.save(user);
        return modelMapper.map(savedUser, UserDTO.class);
    }

    /**
     * Update existing user
     */
    @Transactional
    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Update fields
        existingUser.setName(userDTO.getName());
        existingUser.setEmail(userDTO.getEmail());
        existingUser.setAvatar(userDTO.getAvatar());
        
        // Update roles if provided
        if (userDTO.getRoles() != null) {
            existingUser.setRoles(userDTO.getRoles());
        }
        
        User updatedUser = userRepository.save(existingUser);
        return modelMapper.map(updatedUser, UserDTO.class);
    }

    /**
     * Delete user
     */
    @Transactional
    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }

    /**
     * Get users by role
     */
    public List<UserDTO> getUsersByRole(String role) {
        return userRepository.findByRolesContaining(role).stream()
                .map(user -> modelMapper.map(user, UserDTO.class))
                .collect(Collectors.toList());
    }

    /**
     * Create or update a local user from Keycloak data
     */
    @Transactional
    public UserDTO syncUserFromKeycloak(String keycloakId) {
        // Check if user already exists in our database
        Optional<User> existingUser = userRepository.findByKeycloakId(keycloakId);
        
        // Get user data from Keycloak
        return keycloakService.getUserById(keycloakId)
                .map(keycloakUser -> {
                    User user;
                    if (existingUser.isPresent()) {
                        // Update existing user
                        user = existingUser.get();
                    } else {
                        // Create new user
                        user = new User();
                        user.setKeycloakId(keycloakId);
                    }
                    
                    // Update user data
                    user.setName(keycloakUser.getFirstName() + " " + keycloakUser.getLastName());
                    user.setEmail(keycloakUser.getEmail());
                    
                    // Get roles from Keycloak
                    List<String> roles = keycloakService.getUserRoles(keycloakId);
                    user.setRoles(roles.stream().collect(Collectors.toSet()));
                    
                    // Generate avatar if not already set
                    if (user.getAvatar() == null || user.getAvatar().isEmpty()) {
                        String firstName = keycloakUser.getFirstName();
                        String lastName = keycloakUser.getLastName();
                        String avatar = "";
                        
                        if (firstName != null && !firstName.isEmpty()) {
                            avatar += firstName.substring(0, 1).toUpperCase();
                        }
                        
                        if (lastName != null && !lastName.isEmpty()) {
                            avatar += lastName.substring(0, 1).toUpperCase();
                        }
                        
                        user.setAvatar(avatar);
                    }
                    
                    // Save user
                    User savedUser = userRepository.save(user);
                    return modelMapper.map(savedUser, UserDTO.class);
                })
                .orElse(null);
    }
}
