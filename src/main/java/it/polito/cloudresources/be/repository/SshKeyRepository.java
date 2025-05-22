package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.SshKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for SSH keys
 */
@Repository
public interface SshKeyRepository extends JpaRepository<SshKey, Long> {
    
    /**
     * Find SSH key by user ID
     * 
     * @param userId The Keycloak user ID
     * @return Optional containing the SSH key if found
     */
    Optional<SshKey> findByUserId(String userId);
    
    /**
     * Delete SSH key by user ID
     * 
     * @param userId The Keycloak user ID
     * @return Number of records deleted
     */
    int deleteByUserId(String userId);
}
