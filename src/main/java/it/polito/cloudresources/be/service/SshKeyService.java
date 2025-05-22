package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.users.SshKeyDTO;
import it.polito.cloudresources.be.model.SshKey;
import it.polito.cloudresources.be.repository.SshKeyRepository;
import it.polito.cloudresources.be.util.SshKeyValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service for managing SSH keys in the database
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SshKeyService {
    
    private final SshKeyRepository sshKeyRepository;
    private final SshKeyValidator sshKeyValidator;
    
    /**
     * Get SSH key for a user
     * 
     * @param userId The Keycloak user ID
     * @return Optional containing the SSH key if found
     */
    public Optional<String> getUserSshKey(String userId) {
        return sshKeyRepository.findByUserId(userId)
                .map(SshKey::getSshKey);
    }
    
    /**
     * Save or update SSH key for a user
     * 
     * @param userId The Keycloak user ID
     * @param sshKey The SSH key to save
     * @return true if saved successfully
     */
    @Transactional
    public boolean saveUserSshKey(String userId, String sshKey, String actionBy) {
        try {
            // Validate and format the SSH key
            String formattedKey = sshKeyValidator.formatSshKey(sshKey);
            sshKeyValidator.isValidSshPublicKey(formattedKey);
            
            // Check if user already has an SSH key
            Optional<SshKey> existingKey = sshKeyRepository.findByUserId(userId);
            
            LocalDateTime now = LocalDateTime.now();
            
            if (existingKey.isPresent()) {
                // Update existing key
                SshKey key = existingKey.get();
                key.setSshKey(formattedKey);
                key.setUpdatedAt(now);
                key.setUpdatedBy(actionBy);
                sshKeyRepository.save(key);
            } else {
                // Create new key
                SshKey key = SshKey.builder()
                        .userId(userId)
                        .sshKey(formattedKey)
                        .createdAt(now)
                        .updatedAt(now)
                        .createdBy(actionBy)
                        .updatedBy(actionBy)
                        .build();
                sshKeyRepository.save(key);
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error saving SSH key for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Delete SSH key for a user
     * 
     * @param userId The Keycloak user ID
     * @return true if deleted successfully
     */
    @Transactional
    public boolean deleteUserSshKey(String userId) {
        try {
            int deleted = sshKeyRepository.deleteByUserId(userId);
            return deleted > 0;
        } catch (Exception e) {
            log.error("Error deleting SSH key for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Convert SshKey entity to SshKeyDTO
     * 
     * @param sshKey The SSH key entity
     * @return The SSH key DTO
     */
    public SshKeyDTO toDto(SshKey sshKey) {
        if (sshKey == null) {
            return new SshKeyDTO(null);
        }
        return new SshKeyDTO(sshKey.getSshKey());
    }
    
    /**
     * Get SSH key DTO for a user
     * 
     * @param userId The Keycloak user ID
     * @return The SSH key DTO
     */
    public SshKeyDTO getUserSshKeyDto(String userId) {
        return sshKeyRepository.findByUserId(userId)
                .map(this::toDto)
                .orElse(new SshKeyDTO(null));
    }
}
