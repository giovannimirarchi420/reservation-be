package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.FederationDTO;
import it.polito.cloudresources.be.dto.users.UserDTO;
import it.polito.cloudresources.be.mapper.FederationMapper;
import it.polito.cloudresources.be.mapper.UserMapper;
import it.polito.cloudresources.be.model.AuditLog;
import jakarta.persistence.EntityNotFoundException;
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
        List<FederationDTO> federations = federationMapper.toDto(keycloakService.getAllFederations());
        
        for (FederationDTO federation : federations) {
            int memberCount = keycloakService.getUsersInFederation(federation.getId()).size();
            federation.setMemberCount(memberCount);
        }

        return federations;
    }
    
    /**
     * Get federation by ID
     */
    public FederationDTO getFederationById(String id) throws EntityNotFoundException {
        Optional<GroupRepresentation> keycloakGroup = keycloakService.getFederationById(id);
        FederationDTO federationDTO;
        if (keycloakGroup.isPresent()) {
            federationDTO = federationMapper.toDto(keycloakService.getFederationById(id).get());
        } else {
            throw new EntityNotFoundException("Federation not found"); 
        }
        return federationDTO;
    }
    
    /**
     * Create new federation
     */
    public FederationDTO createFederation(FederationDTO federationDTO) {        
        String federationId = keycloakService.createFederation(federationDTO.getName(), federationDTO.getDescription());
        
        if (federationId != null) {
            // Log the action
            auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                    AuditLog.LogAction.CREATE,
                    new AuditLog.LogEntity("FEDERATION", federationId),
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
            auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                    AuditLog.LogAction.UPDATE,
                    new AuditLog.LogEntity("FEDERATION", id),
                    "Updated federation: " + group.getName());
            
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
            auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                    AuditLog.LogAction.DELETE,
                    new AuditLog.LogEntity("FEDERATION", id),
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
            auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                    AuditLog.LogAction.UPDATE,
                    new AuditLog.LogEntity("FEDERATION-USER", federationId),
                    "Added user " + userId + " to federation " + federationId);
        }
        
        return added;
    }
    
    /**
     * Remove user from federation
     */
    public boolean removeUserFromFederation(String userId, String federationId) {
        boolean removed = keycloakService.removeUserFromFederation(userId, federationId);
        
        if (removed) {
            auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                    AuditLog.LogAction.DELETE,
                    new AuditLog.LogEntity("FEDERATION-USER", federationId),
                    "Deleted user " + userId + " from federation " + federationId);
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