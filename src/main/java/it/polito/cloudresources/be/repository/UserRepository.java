package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity operations
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Find a user by email address
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find a user by username
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Find a user by Keycloak ID
     */
    Optional<User> findByKeycloakId(String keycloakId);
    
    /**
     * Check if a user with given email exists
     */
    Boolean existsByEmail(String email);
    
    /**
     * Check if a user with given username exists
     */
    Boolean existsByUsername(String username);
    
    /**
     * Find users by role
     */
    List<User> findByRolesContaining(String role);
}