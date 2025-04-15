package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.EventDTO;
import it.polito.cloudresources.be.dto.ResourceDTO;
import it.polito.cloudresources.be.dto.ResourceTypeDTO;
import it.polito.cloudresources.be.dto.SiteDTO;
import it.polito.cloudresources.be.dto.users.UserDTO;
import it.polito.cloudresources.be.mapper.SiteMapper;
import it.polito.cloudresources.be.mapper.UserMapper;
import it.polito.cloudresources.be.model.AuditLog;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for site operations using Keycloak groups
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SiteService {
    private final KeycloakService keycloakService;
    private final AuditLogService auditLogService;
    private final SiteMapper siteMapper;
    private final UserMapper userMapper;
    private final ResourceTypeService resourceTypeService;
    private final EventService eventService;
    private final ResourceService resourceService;

    /**
     * Get all sites
     */
    public List<SiteDTO> getAllSites(String userId) {
        List<SiteDTO> sites = siteMapper.toDto(keycloakService.getUserGroups(userId));

        for (SiteDTO site : sites) {
            int memberCount = keycloakService.getUsersInGroup(site.getId()).size();
            site.setMemberCount(memberCount);
        }

        return sites;
    }
    
    /**
     * Get site by ID
     */
    public SiteDTO getSiteById(String id, String userId) throws AccessDeniedException {
        Optional<GroupRepresentation> keycloakGroup = keycloakService.getGroupById(id);

        if (!keycloakService.hasGlobalAdminRole(userId) &&
            !keycloakService.isUserInGroup(userId, id)) {
            throw new AccessDeniedException("User does not have permission");
        }

        SiteDTO siteDTO;
        if (!keycloakGroup.isPresent()) {
            throw new EntityNotFoundException("Site not found");
        }

        siteDTO = siteMapper.toDto(keycloakGroup.get());

        return siteDTO;
    }
    
    /**
     * Create new site
     */
    public SiteDTO createSite(SiteDTO siteDTO, String userId, boolean privateSite) {
        String siteId = keycloakService.setupNewKeycloakGroup(siteDTO.getName(), siteDTO.getDescription(), privateSite);

        if(Objects.isNull(siteId)) {
            throw new RuntimeException("An error occurred creating the site, please try again");
        }

        keycloakService.assignSiteAdminRole(userId, siteDTO.getName());

        // If private site, no user will be added, but at least the creator must be added in order to operate on it
        if(privateSite)
            keycloakService.addUserToKeycloakGroup(userId, siteId);

        // Log the action
        auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                AuditLog.LogAction.CREATE,
                new AuditLog.LogEntity("site", siteId),
                "Created site: " + siteDTO.getName());

        int memberCount = keycloakService.getUsersInGroup(siteId).size();
        SiteDTO siteOutputDto = keycloakService.getGroupById(siteId)
                .map(siteMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Site created but could not be retrieved"));
        
        siteOutputDto.setMemberCount(memberCount);
        return siteOutputDto;
    }
    
    /**
     * Update existing site
     */
    public Optional<SiteDTO> updateSite(String id, SiteDTO siteDTO) { 
        //TODO: Do not allow to update the name, otherwise will be problem with the role system
        // Convert DTO to GroupRepresentation using mapper
        GroupRepresentation group = siteMapper.toEntity(siteDTO);
        
        boolean updated = keycloakService.updateGroup(id, group);
        
        if (updated) {
            // Log the action
            auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                    AuditLog.LogAction.UPDATE,
                    new AuditLog.LogEntity("site", id),
                    "Updated site: " + group.getName());
            
            // Get updated site to return
            return keycloakService.getGroupById(id)
                    .map(siteMapper::toDto);
        }
        
        return Optional.empty();
    }
    
    /**
     * Delete site
     */
    @Transactional
    public void deleteSite(String id, String userId) throws AccessDeniedException {
        // Get site name for logging before deletion
        Optional<GroupRepresentation> groupRepresentationOpt = keycloakService.getGroupById(id);

        if(!groupRepresentationOpt.isPresent()) {
            throw new EntityNotFoundException("Group not found");
        }

        String siteName = groupRepresentationOpt.get().getName();

        if (!keycloakService.getUserAdminGroups(userId).contains(siteName) &&
                !keycloakService.hasGlobalAdminRole(userId)) {
            throw new AccessDeniedException("Only site admin can delete sites");
        }

        //To remove a site, all events, resources and resource types must be deleted too.
        List<EventDTO> eventDTOs = eventService.getEventsBySite(id, userId);
        List<ResourceDTO> resourceDTOs = resourceService.getResourcesBySite(id, userId);
        List<ResourceTypeDTO> resourceTypesDTOs = resourceTypeService.getAllResourceTypes(userId, id);

        for(EventDTO eventDTO : eventDTOs) {
            eventService.deleteEvent(eventDTO.getId(), userId);
        }

        for(ResourceDTO resourceDTO: resourceDTOs) {
            resourceService.deleteResource(resourceDTO.getId(), userId);
        }

        for(ResourceTypeDTO resourceTypeDTO : resourceTypesDTOs) {
            resourceTypeService.deleteResourceType(resourceTypeDTO.getId(), userId);
        }

        boolean deleted = keycloakService.deleteGroup(id);

        if (deleted) {
            auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                    AuditLog.LogAction.DELETE,
                    new AuditLog.LogEntity("site", id),
                    "Deleted site: " + siteName);
        } else {
            throw new RuntimeException("Error deleting the site " + id);
        }
    }
    
    /**
     * Get users in site
     * @param siteId
     */
    public List<UserDTO> getUsersInSite(String siteId, String userId) throws AccessDeniedException {
        // Check if user has access to this site
        if (!keycloakService.hasGlobalAdminRole(userId) &&
                !keycloakService.isUserSiteAdmin(userId, siteId)) {
            throw new AccessDeniedException("User can't access this site");
        }
        return userMapper.toDto(keycloakService.getUsersInGroup(siteId));
    }

    public List<UserDTO> getUsersSiteAdmins(String siteId, String userId) throws AccessDeniedException {
        List<UserDTO> allSiteUsers = getUsersInSite(siteId, userId); //Role checking is performed internally in this method
        return allSiteUsers.stream()
                .filter(user -> keycloakService.isUserSiteAdmin(user.getId(), siteId)).toList();
    }
    
    /**
     * Add user to site
     */
    public void addUserToSite(String userId, String siteId, String requesterUserId) throws AccessDeniedException {
        if (!keycloakService.hasGlobalAdminRole(requesterUserId) &&
                !keycloakService.isUserSiteAdmin(requesterUserId, siteId)) {
            throw new AccessDeniedException("User can't add new users to this site");
        }

        boolean added = keycloakService.addUserToKeycloakGroup(userId, siteId);
        
        if (added) {
            // Log the action
            auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                    AuditLog.LogAction.UPDATE,
                    new AuditLog.LogEntity("SITE-USER", siteId),
                    "Added user " + userId + " to site " + siteId);
        } else {
            throw new RuntimeException("Error adding new user to the site, please try again");
        }
        

    }
    
    /**
     * Remove user from site
     */
    public void removeUserFromSite(String userId, String siteId, String requesterUserId) throws AccessDeniedException {

        if (!keycloakService.hasGlobalAdminRole(requesterUserId) &&
                !keycloakService.isUserSiteAdmin(requesterUserId, siteId)) {
            throw new AccessDeniedException("User can't add new users to this site");
        }

        // Prevent removing yourself from a site
        if (userId.equals(requesterUserId) && keycloakService.isUserSiteAdmin(requesterUserId, siteId)) {
            throw new IllegalStateException("Sites admins cannot remove themselves from their site");
        }
        
        keycloakService.removeUserFromSite(userId, siteId);
        
        auditLogService.logCrudAction(AuditLog.LogType.ADMIN,
                AuditLog.LogAction.DELETE,
                new AuditLog.LogEntity("SITE-USER", siteId),
                "Deleted user " + userId + " from site " + siteId);
    }
    
    /**
     * Get sites for a user
     */
    public List<SiteDTO> getUserSites(String userId) {
        return keycloakService.getUserGroups(userId).stream()
                .map(siteMapper::toDto)
                .collect(Collectors.toList());
    }
}