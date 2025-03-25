package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.FederationDTO;
import it.polito.cloudresources.be.dto.UserDTO;
import it.polito.cloudresources.be.mapper.FederationMapper;
import it.polito.cloudresources.be.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for federation operations using Keycloak groups
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FederationService {
    private final KeycloakService keycloakService;
    private final AuditLogService auditLogService;
    private final FederationMapper federationMapper;
    private final UserMapper userMapper;
    
    /**
     * Get all federations
     */
    public List<FederationDTO> getAllFederations() {
        return federationMapper.toDto(keycloakService.getAllFederations());
    }
    
    /**
     * Get federation by ID
     */
    public Optional<FederationDTO> getFederationById(String id) {
        return keycloakService.getFederationById(id)
                .map(federationMapper::toDto);
    }
    
    /**
     * Create new federation
     */
    public FederationDTO createFederation(FederationDTO federationDTO) {        
        String federationId = keycloakService.createFederation(federationDTO.getName(), federationDTO.getDescription());
        
        if (federationId != null) {
            // Log the action
            auditLogService.logAdminAction("Federation", "create", 
                    "Created federation: " + federationDTO.getName());
            
            // Get created federation to return full details
            return keycloakService.getFederationById(federationId)
                    .map(federationMapper::toDto)
                    .orElseThrow(() -> new RuntimeException("Federation created but could not be retrieved"));
        }
        
        throw new RuntimeException("Failed to create federation");
    }
    
    /**
     * Update existing federation
     */
    public Optional<FederationDTO> updateFederation(String id, FederationDTO federationDTO) {
        // Convert DTO to GroupRepresentation using mapper
        GroupRepresentation group = federationMapper.toEntity(federationDTO);
        
        boolean updated = keycloakService.updateFederation(id, group);
        
        if (updated) {
            // Log the action
            auditLogService.logAdminAction("Federation", "update", 
                    "Updated federation: " + federationDTO.getName());
            
            // Get updated federation to return
            return keycloakService.getFederationById(id)
                    .map(federationMapper::toDto);
        }
        
        return Optional.empty();
    }
    
    /**
     * Delete federation
     */
    public boolean deleteFederation(String id) {
        // Get federation name for logging before deletion
        String federationName = keycloakService.getFederationById(id)
                .map(GroupRepresentation::getName)
                .orElse("Unknown");
        
        boolean deleted = keycloakService.deleteFederation(id);
        
        if (deleted) {
            // Log the action
            auditLogService.logAdminAction("Federation", "delete", 
                    "Deleted federation: " + federationName);
        }
        
        return deleted;
    }
    
    /**
     * Get users in federation
     */
    public List<UserDTO> getUsersInFederation(String federationId) {
        return userMapper.toDto(keycloakService.getUsersInFederation(federationId));
    }
    
    /**
     * Add user to federation
     */
    public boolean addUserToFederation(String userId, String federationId) {
        boolean added = keycloakService.addUserToFederation(userId, federationId);
        
        if (added) {
            // Log the action
            auditLogService.logAdminAction("Federation", "addUser", 
                    "Added user ID: " + userId + " to federation ID: " + federationId);
        }
        
        return added;
    }
    
    /**
     * Remove user from federation
     */
    public boolean removeUserFromFederation(String userId, String federationId) {
        boolean removed = keycloakService.removeUserFromFederation(userId, federationId);
        
        if (removed) {
            // Log the action
            auditLogService.logAdminAction("Federation", "removeUser", 
                    "Removed user ID: " + userId + " from federation ID: " + federationId);
        }
        
        return removed;
    }
    
    /**
     * Check if user is in federation
     */
    public boolean isUserInFederation(String userId, String federationId) {
        return keycloakService.isUserInFederation(userId, federationId);
    }
    
    /**
     * Get federations for a user
     */
    public List<FederationDTO> getUserFederations(String userId) {
        List<String> federationIds = keycloakService.getUserFederations(userId);
        
        return federationIds.stream()
                .map(keycloakService::getFederationById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(federationMapper::toDto)
                .collect(Collectors.toList());
    }
}