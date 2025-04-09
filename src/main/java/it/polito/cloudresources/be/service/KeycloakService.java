package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.users.UserDTO;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced service for interacting with Keycloak as the single source of truth for user data
 */
@Service
@Profile("!dev") // Active in all profiles except dev
@Slf4j
public class KeycloakService {

    public static final String ATTR_SSH_KEY = "ssh_key";
    public static final String ATTR_AVATAR = "avatar";

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.resource}")
    private String clientId;

    @Value("${keycloak.credentials.secret}")
    private String clientSecret;

    // Admin credentials - in a production environment should come from secure configuration
    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:admin}")
    private String adminPassword;

    /**
     * Creates an admin Keycloak client
     */
    protected Keycloak getKeycloakClient() {
        return KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm("master")
                .clientId("admin-cli")
                .username(adminUsername)
                .password(adminPassword)
                .build();
    }

    /**
     * Get the realm resource
     */
    protected RealmResource getRealmResource() {
        return getKeycloakClient().realm(realm);
    }

    /**
     * Get all Keycloak users
     */
    public List<UserRepresentation> getUsers() {
        try {
            return getRealmResource().users().list();
        } catch (Exception e) {
            log.error("Error fetching users from Keycloak", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get a user by username
     */
    public Optional<UserRepresentation> getUserByUsername(String username) {
        try {
            List<UserRepresentation> users = getRealmResource().users().search(username, true);
            return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
        } catch (Exception e) {
            log.error("Error fetching user from Keycloak", e);
            return Optional.empty();
        }
    }

    /**
     * Get a user by email
     */
    public Optional<UserRepresentation> getUserByEmail(String email) {
        try {
            List<UserRepresentation> users = getRealmResource().users().search(null, null, null, email, 0, 1);
            return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
        } catch (Exception e) {
            log.error("Error fetching user from Keycloak by email", e);
            return Optional.empty();
        }
    }

    /**
     * Get a user by ID
     */
    public Optional<UserRepresentation> getUserById(String id) {
        try {
            UserRepresentation user = getRealmResource().users().get(id).toRepresentation();
            return Optional.ofNullable(user);
        } catch (Exception e) {
            log.error("Error fetching user from Keycloak", e);
            return Optional.empty();
        }
    }

    /**
     * Creates a new user in Keycloak from a UserDTO
     *
     * @param userDTO the user data transfer object
     * @param password the user's password
     * @param roles the roles to assign to the user
     * @return the ID of the created user, or null if creation failed
     */
    @Transactional
    public String createUser(UserDTO userDTO, String password, Set<String> roles) {
        try {
            log.debug("Attempting to create user: username={}, email={}, firstName={}, lastName={}",
                    userDTO.getUsername(), userDTO.getEmail(), userDTO.getFirstName(), userDTO.getLastName());

            // Create user representation
            UserRepresentation user = createUserRepresentation(userDTO);

            // Create user in Keycloak
            String userId = createUserInKeycloak(user);
            if (userId == null) {
                return null;
            }

            // Set user password
            if (!setUserPassword(userId, password)) {
                log.warn("Password could not be set for user: {}", userId);
                // Continue anyway as the user has been created
            }

            assignGroupsToUser(userId);

            // Assign roles to user TODO: Manage federation
            if (roles != null && !roles.isEmpty()) {

            }

            return userId;
        } catch (Exception e) {
            log.error("Error creating user in Keycloak", e);
            return null;
        }
    }

    /**
     * Update an existing user in Keycloak
     */
    @Transactional
    public boolean updateUser(String userId, Map<String, Object> attributes) {
        try {
            UserResource userResource = getRealmResource().users().get(userId);
            UserRepresentation user = userResource.toRepresentation();

            if (attributes.containsKey("email")) {
                user.setEmail((String) attributes.get("email"));
            }

            if (attributes.containsKey("firstName")) {
                user.setFirstName((String) attributes.get("firstName"));
            }

            if (attributes.containsKey("lastName")) {
                user.setLastName((String) attributes.get("lastName"));
            }

            if (attributes.containsKey("username")) {
                user.setUsername((String) attributes.get("username"));
            }

            if (attributes.containsKey("enabled")) {
                user.setEnabled((Boolean) attributes.get("enabled"));
            }

            // Handle SSH key and avatar attributes
            Map<String, List<String>> userAttributes = user.getAttributes();
            if (userAttributes == null) {
                userAttributes = new HashMap<>();
            }

            if (attributes.containsKey(ATTR_SSH_KEY)) {
                String sshKey = (String) attributes.get(ATTR_SSH_KEY);
                if (sshKey != null && !sshKey.isEmpty()) {
                    userAttributes.put(ATTR_SSH_KEY, Collections.singletonList(sshKey));
                } else {
                    userAttributes.remove(ATTR_SSH_KEY);
                }
            }

            if (attributes.containsKey(ATTR_AVATAR)) {
                String avatar = (String) attributes.get(ATTR_AVATAR);
                if (avatar != null && !avatar.isEmpty()) {
                    userAttributes.put(ATTR_AVATAR, Collections.singletonList(avatar));
                } else {
                    userAttributes.remove(ATTR_AVATAR);
                }
            }

            user.setAttributes(userAttributes);

            // Update basic user info first
            userResource.update(user);
            log.debug("Basic user info updated for user: {}", userId);

            // Update password if provided
            if (attributes.containsKey("password")) {
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue((String) attributes.get("password"));
                credential.setTemporary(false);

                userResource.resetPassword(credential);
                log.debug("Password updated for user: {}", userId);
            }

            log.info("User updated in Keycloak: {}", userId);
            return true;
        } catch (Exception e) {
            log.error("Error updating user in Keycloak", e);
            return false;
        }
    }

    /**
     * Delete a user from Keycloak
     */
    public boolean deleteUser(String userId) {
        try {
            getRealmResource().users().get(userId).remove();
            log.info("User deleted from Keycloak: {}", userId);
            return true;
        } catch (Exception e) {
            log.error("Error deleting user from Keycloak", e);
            return false;
        }
    }

    /**
     * Get roles of a user
     */
    public List<String> getUserRoles(String userId) {
        try {
            List<RoleRepresentation> roles = getRealmResource().users().get(userId).roles().realmLevel().listAll();
            return roles.stream().map(RoleRepresentation::getName).toList();
        } catch (Exception e) {
            log.error("Error fetching user roles from Keycloak", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get specific attribute for a user
     */
    public Optional<String> getUserAttribute(String userId, String attributeName) {
        try {
            UserRepresentation user = getRealmResource().users().get(userId).toRepresentation();
            Map<String, List<String>> attributes = user.getAttributes();
            
            if (attributes != null && attributes.containsKey(attributeName)) {
                List<String> values = attributes.get(attributeName);
                if (!values.isEmpty()) {
                    return Optional.of(values.get(0));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error fetching user attribute from Keycloak", e);
            return Optional.empty();
        }
    }

    /**
     * Get SSH key for a user
     */
    public Optional<String> getUserSshKey(String userId) {
        return getUserAttribute(userId, ATTR_SSH_KEY);
    }

    /**
     * Get avatar for a user
     */
    public Optional<String> getUserAvatar(String userId) {
        return getUserAttribute(userId, ATTR_AVATAR);
    }

    /**
     * Create realm roles in Keycloak if they don't exist
     */
    public void ensureRealmRoles(String... roleNames) {
        try {
            for (String roleName : roleNames) {
                if (getRealmResource().roles().get(roleName).toRepresentation() == null) {
                    RoleRepresentation role = new RoleRepresentation();
                    role.setName(roleName);
                    getRealmResource().roles().create(role);
                    log.info("Created role in Keycloak: {}", roleName);
                }
            }
        } catch (Exception e) {
            log.error("Error ensuring realm roles in Keycloak", e);
        }
    }

    /**
     * Find users by role
     */
    public List<UserRepresentation> getUsersByRole(String roleName) {
        try {
            List<UserRepresentation> allUsers = getUsers();
            List<UserRepresentation> usersWithRole = new ArrayList<>();
            
            for (UserRepresentation user : allUsers) {
                List<String> userRoles = getUserRoles(user.getId());
                if (userRoles.contains(roleName) || userRoles.contains(roleName.toUpperCase()) || 
                    userRoles.contains(roleName.toLowerCase())) {
                    usersWithRole.add(user);
                }
            }
            
            return usersWithRole;
        } catch (Exception e) {
            log.error("Error finding users by role from Keycloak", e);
            return Collections.emptyList();
        }
    }

    public List<GroupRepresentation> getAllGroups() {
        return getRealmResource().groups().groups();
    }
    
    public Optional<GroupRepresentation> getGroupById(String groupId) {
        try {
            GroupRepresentation group = getRealmResource().groups().group(groupId).toRepresentation();
            return Optional.of(group);
        } catch (Exception e) {
            log.error("Error fetching site", e);
            return Optional.empty();
        }
    }

    public Optional<GroupRepresentation> getGroupByName(String groupName) {
        try {
            return getRealmResource().groups().groups().stream()
                    .filter(group -> group.getName() == groupName)
                    .findFirst();
        } catch (Exception e) {
            log.error("Error fetching site", e);
            return Optional.empty();
        }
    }
    /**
     * Create a site from a GroupRepresentation
     */
    public String setupNewKeycloakGroup(GroupRepresentation group) {
        try {
            Response response = getRealmResource().groups().add(group);
            if (response.getStatus() == 201) {
                String locationPath = response.getLocation().getPath();
                String siteId = locationPath.substring(locationPath.lastIndexOf('/') + 1);
                log.info("Created group with ID: {}", siteId);
                return siteId;
            } else {
                log.error("Failed to create group. Status: {}", response.getStatus());
            }
        } catch (Exception e) {
            log.error("Error creating group", e);
        }
        return null;
    }

    /**
     * Creates a new site
     */
    public String setupNewKeycloakGroup(String name, String description) {
        GroupRepresentation group = new GroupRepresentation();
        group.setName(name);

        // Set attributes for the description
        Map<String, List<String>> attributes = new HashMap<>();
        if (description != null && !description.isEmpty()) {
            attributes.put("description", Collections.singletonList(description));
            group.setAttributes(attributes);
        }

        String groupId = setupNewKeycloakGroup(group);

        // Create the site admin role
        if (groupId != null) {
            createSiteAdminRole(name);
        }

        // When a new site is created, add all existing users to it
        if (groupId != null) {
            List<UserRepresentation> allUsers = getUsers();
            for (UserRepresentation user : allUsers) {
                addUserToKeycloakGroup(user.getId(), groupId);
            }
        }
        return groupId;
    }
    
    /**
     * Update an existing site
     */
    public boolean updateGroup(String groupId, GroupRepresentation updatedGroup) {
        try {
            // First get the current group to ensure it exists
            GroupResource groupResource = getRealmResource().groups().group(groupId);
            GroupRepresentation currentGroup = groupResource.toRepresentation();
            
            // We want to preserve the ID when updating
            updatedGroup.setId(groupId);
            
            // For subgroups, preserve the existing ones if not specified in the update
            if (updatedGroup.getSubGroups() == null && currentGroup.getSubGroups() != null) {
                updatedGroup.setSubGroups(currentGroup.getSubGroups());
            }
            
            // Update the group
            groupResource.update(updatedGroup);
            log.info("Updated site with ID: {}", groupId);
            return true;
        } catch (Exception e) {
            log.error("Error updating site with ID: {}", groupId, e);
            return false;
        }
    }
    
    /**
     * Delete a site
     * @param groupId
     */
    public boolean deleteGroup(String groupId) {
        try {
            // Check if the site exists
            GroupResource groupResource = getRealmResource().groups().group(groupId);
            GroupRepresentation group = groupResource.toRepresentation();
            String roleToRemove = getSiteAdminRoleName(group.getName());

            if (group != null) {
                // Delete the site
                groupResource.remove();
                log.info("Deleted site with ID: {}", groupId);
                getRealmResource().roles().deleteRole(roleToRemove);
                log.info("Deleted role: {}", roleToRemove);
                return true;
            } else {
                log.warn("Site with ID {} not found for deletion", groupId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error deleting site with ID: {}", groupId, e);
            return false;
        }
    }

    /**
     * Check if a user is a member of a specific site (group)
     */
    public boolean isUserInGroup(String userId, String groupId) {
        try {
            UserResource userResource = getRealmResource().users().get(userId);
            List<GroupRepresentation> userGroups = userResource.groups();
            
            // Check if any of the user's groups matches the site ID
            return userGroups.stream()
                .anyMatch(group -> group.getId().equals(groupId));
        } catch (Exception e) {
            log.error("Error checking if user {} is in group {}", userId, groupId, e);
            return false;
        }
    }

    /**
     * Adds a user to a site
     *
     * @param userId the user ID
     * @param groupId the site ID
     * @return true if user was added to site successfully, false otherwise
     */
    public boolean addUserToKeycloakGroup(String userId, String groupId) {
        try {
            // Check if user is already in the site
            if (isUserInGroup(userId, groupId)) {
                log.info("User {} is already in site {}", userId, groupId);
                return true;
            }
            
            // Add user to site group
            UserResource userResource = getRealmResource().users().get(userId);
            userResource.joinGroup(groupId);
            
            log.info("Added user {} to site {}", userId, groupId);
            return true;
        } catch (Exception e) {
            log.error("Error adding user {} to site {}", userId, groupId, e);
            return false;
        }
    }



    /**
     * Generates a standardized site admin role name
     */
    public String getSiteAdminRoleName(String siteName) {
        return siteName.toLowerCase().replace(' ', '_') + "_site_admin";
    }

    /**
     * Creates a site user role when a new site is created
     */
    public boolean createSiteAdminRole(String siteName) {
        try {
            String roleName = getSiteAdminRoleName(siteName);

            // Create the role if it doesn't exist
            if (getRealmResource().roles().get(roleName).toRepresentation() == null) {
                RoleRepresentation role = new RoleRepresentation();
                role.setName(roleName);
                role.setDescription("User role for site: " + siteName);
                getRealmResource().roles().create(role);
                log.info("Created site user role: {}", roleName);
            }
            return true;
        } catch (Exception e) {
            log.error("Error creating site user role: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Assigns the site admin role to a user
     */
    public boolean assignSiteAdminRole(String userId, String siteName) {
        try {
            // First ensure the role exists
            createSiteAdminRole(siteName);

            // Then assign it to the user
            String roleName = getSiteAdminRoleName(siteName);
            return assignRoleToUser(userId, roleName);
        } catch (Exception e) {
            log.error("Error assigning site role to user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    private String getGroupNameById(String groupId) {
        return getRealmResource().groups().group(groupId).toRepresentation().getName();
    }

    /**
     * Removes the site admin role from a user
     */
    public void removeSiteAdminRole(String userId, String siteId, String requesterUserId) throws AccessDeniedException {
        if (!hasGlobalAdminRole(requesterUserId) &&
                !isUserSiteAdmin(requesterUserId, siteId)) {
            throw new AccessDeniedException("User can't remove site admin role in this site");
        }

        String roleName = getSiteAdminRoleName(getGroupNameById(siteId));

        UserResource userResource = getRealmResource().users().get(userId);
        RoleRepresentation role = getRealmResource().roles().get(roleName).toRepresentation();

        if (Objects.nonNull(role)) {
            userResource.roles().realmLevel().remove(Collections.singletonList(role));
            log.info("Removed site admin role {} from user {}", roleName, userId);
        } else {
            throw new RuntimeException("Error removing site admin role, please try again");
        }
    }

    /**
     * Checks if a user is an admin of a site
     */
    public boolean isUserSiteAdmin(String userId, String siteId) {
        // Global admins are implicitly site admins
        if (hasGlobalAdminRole(userId)) {
            return true;
        }

        try {
            // Get the site name for the site ID
            Optional<GroupRepresentation> site = getGroupById(siteId);
            if (site.isEmpty()) {
                return false;
            }

            // Check if user has the site admin role
            String roleName = getSiteAdminRoleName(site.get().getName());
            List<String> userRoles = getUserRoles(userId);
            return userRoles.contains(roleName);
        } catch (Exception e) {
            log.error("Error checking if user is site admin: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Make a user a site admin
     */
    public boolean makeSiteAdmin(String userId, String siteId, String requesterUserId) throws AccessDeniedException {
        if (!hasGlobalAdminRole(requesterUserId) &&
                !isUserSiteAdmin(requesterUserId, siteId)) {
            throw new AccessDeniedException("User can't add new users to this site");
        }

        try {

            // First ensure the user is a member of the site
            if (!isUserInGroup(userId, siteId)) {
                addUserToKeycloakGroup(userId, siteId);
            }

            // Get the site name
            Optional<GroupRepresentation> site = getGroupById(siteId);
            if (site.isEmpty()) {
                return false;
            }

            // Assign the site admin role
            return assignSiteAdminRole(userId, site.get().getName());
        } catch (Exception e) {
            log.error("Error making user {} admin of site {}: {}", userId, siteId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get sites where user is an admin
     */
    public List<String> getUserAdminGroups(String userId) {
        try {
            // First check if the user exists
            UserRepresentation user = getRealmResource().users().get(userId).toRepresentation();
            
            // We need to get the user's roles directly instead of relying on groups
            List<String> roles = getUserRoles(userId);
            
            // Filter roles that match the site admin pattern: {sitename}_site_admin
            return roles.stream()
                .filter(role -> role.endsWith("_site_admin"))
                .map(role -> {
                    // Extract site name from the role name
                    // The format is {sitename}_site_admin, so we remove "_site_admin"
                    return role.substring(0, role.length() - "_site_admin".length());
                })
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Error getting admin sites for user {}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * Check if a user has the GLOBAL_ADMIN role
     */
    public boolean hasGlobalAdminRole(String userId) {
        try {
            List<String> roles = getUserRoles(userId);
            return roles.contains("global_admin");
        } catch (Exception e) {
            log.error("Error checking if user {} has GLOBAL_ADMIN role", userId, e);
            return false;
        }
    }
    
    /**
     * Assign a role to a user
     */
    private boolean assignRoleToUser(String userId, String roleName) {
        try {
            UserResource userResource = getRealmResource().users().get(userId);
            RoleRepresentation role = getRealmResource().roles().get(roleName).toRepresentation();
            
            userResource.roles().realmLevel().add(Collections.singletonList(role));
            log.info("Assigned role {} to user {}", roleName, userId);
            return true;
        } catch (Exception e) {
            log.error("Error assigning role {} to user {}", roleName, userId, e);
            return false;
        }
    }

    /**
     * Get users who are members of a specific site (group)
     * @param groupId
     */
    public List<UserRepresentation> getUsersInGroup(String groupId) {
        try {
            // Get the site (group) resource
            GroupResource groupResource = getRealmResource().groups().group(groupId);
            
            // Fetch all members of the group
            List<UserRepresentation> members = groupResource.members();
            
            log.debug("Found {} users in site {}", members.size(), groupId);
            return members;
        } catch (Exception e) {
            log.error("Error getting users in site {}", groupId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Remove a user from a site (group)
     */
    public boolean removeUserFromSite(String userId, String siteId) {
        try {
            // Check if user is actually in the site
            if (!isUserInGroup(userId, siteId)) {
                log.info("User {} is not in site {}, nothing to remove", userId, siteId);
                return true; // Not an error since the end state is what was desired
            }

            // Get the user resource
            UserResource userResource = getRealmResource().users().get(userId);
            
            // Remove the user from the group
            userResource.leaveGroup(siteId);
            
            // Verify removal was successful
            boolean stillInSite = isUserInGroup(userId, siteId);
            if (stillInSite) {
                log.warn("Failed to remove user {} from site {} - user is still a member", userId, siteId);
                return false;
            }
            
            log.info("Successfully removed user {} from site {}", userId, siteId);
            return true;
        } catch (Exception e) {
            log.error("Error removing user {} from site {}: {}", userId, siteId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get all sites ids (groups) a user belongs to
     */
    public List<String> getUserSites(String userId) {
        try {
            // Get the user resource
            UserResource userResource = getRealmResource().users().get(userId);
            
            // Get all groups the user belongs to
            List<GroupRepresentation> userGroups = userResource.groups();
            
            // Extract the site IDs (group IDs)
            List<String> siteIds = userGroups.stream()
                .map(GroupRepresentation::getId)
                .collect(Collectors.toList());
            
            log.debug("User {} belongs to {} sites", userId, siteIds.size());
            return siteIds;
        } catch (Exception e) {
            log.error("Error retrieving sites for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get all sites a user belongs to as GroupRepresentations
     */
    public List<GroupRepresentation> getUserGroups(String userId) {
        try {
            // Get the user resource
            UserResource userResource = getRealmResource().users().get(userId);
            
            // Get all groups the user belongs to
            List<GroupRepresentation> userGroups = userResource.groups();
            
            log.debug("User {} belongs to {} site groups", userId, userGroups.size());
            return userGroups;
        } catch (Exception e) {
            log.error("Error retrieving site groups for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Creates a UserRepresentation object from a UserDTO
     *
     * @param userDTO the user data transfer object
     * @return the created UserRepresentation
     */
    private UserRepresentation createUserRepresentation(UserDTO userDTO) {
        UserRepresentation user = new UserRepresentation();

        // Set basic fields
        user.setUsername(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEnabled(true);
        user.setEmailVerified(true);

        // Set attributes
        Map<String, List<String>> attributes = createUserAttributes(userDTO);
        user.setAttributes(attributes);

        return user;
    }

    /**
     * Creates a map of user attributes from a UserDTO
     *
     * @param userDTO the user data transfer object
     * @return the map of user attributes
     */
    private Map<String, List<String>> createUserAttributes(UserDTO userDTO) {
        Map<String, List<String>> attributes = new HashMap<>();

        // Add SSH key if provided
        if (userDTO.getSshPublicKey() != null && !userDTO.getSshPublicKey().isEmpty()) {
            attributes.put(ATTR_SSH_KEY, Collections.singletonList(userDTO.getSshPublicKey()));
        }

        // Add avatar if provided
        if (userDTO.getAvatar() != null && !userDTO.getAvatar().isEmpty()) {
            attributes.put(ATTR_AVATAR, Collections.singletonList(userDTO.getAvatar()));
        }

        return attributes;
    }

    /**
     * Creates the user in Keycloak
     *
     * @param user the user representation
     * @return the ID of the created user, or null if creation failed
     */
    private String createUserInKeycloak(UserRepresentation user) {
        UsersResource usersResource = getRealmResource().users();

        // Detailed log of the user representation
        log.debug("User representation: {}", user);

        Response response = usersResource.create(user);
        log.debug("User creation response - Status: {}, Message: {}",
                response.getStatus(), response.getStatusInfo().getReasonPhrase());

        if (response.getStatus() != 201) {
            if (response.hasEntity()) {
                // Try to read the response body to better understand the error
                String responseBody = response.readEntity(String.class);
                log.error("Error details from Keycloak: {}", responseBody);
            }
            log.error("User creation failed in Keycloak: {} ({})",
                    response.getStatusInfo().getReasonPhrase(), response.getStatus());
            return null;
        }

        // Get created user ID
        String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
        log.info("User created in Keycloak with ID: {}", userId);

        return userId;
    }

    /**
     * Sets the password for a user
     *
     * @param userId the user ID
     * @param password the password to set
     * @return true if password was set successfully, false otherwise
     */
    private boolean setUserPassword(String userId, String password) {
        try {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(false);

            getRealmResource().users().get(userId).resetPassword(credential);
            log.debug("Password successfully set for user: {}", userId);
            return true;
        } catch (Exception e) {
            log.error("Error setting password for user: {}", userId, e);
            return false;
        }
    }

    /**
     * Assigns groups to a user
     *
     * @param userId the user ID
     * @return true if groups were assigned successfully, false otherwise
     */
    private boolean assignGroupsToUser(String userId) {
        try {
            log.debug("Attempting to assign groups to user: {}", userId);
            
            // Get the user resource
            UserResource userResource = getRealmResource().users().get(userId);
            
            // Get all groups in the realm
            List<GroupRepresentation> groups = getRealmResource().groups().groups();
            
            // Join each group individually
            for (GroupRepresentation group : groups) {
                userResource.joinGroup(group.getId());
                log.debug("Added user {} to group {}", userId, group.getName());
            }
            
            log.debug("All groups successfully assigned to user: {}", userId);
            return true;
        } catch (Exception e) {
            log.error("Error assigning groups to user: {}", userId, e);
            return false;
        }
    }

}