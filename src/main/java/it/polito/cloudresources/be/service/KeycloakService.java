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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import it.polito.cloudresources.be.dto.FederationDTO;
import it.polito.cloudresources.be.mapper.FederationMapper;

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
    
    @Autowired
    private FederationMapper federationMapper;

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

            // Assign roles to user
            if (roles != null && !roles.isEmpty()) {
                assignRolesToUser(userId, roles);
            }

            // Add user to federation
            addUserToFederation(userId, userDTO.getFederationId());

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

            // Update roles if provided
            if (attributes.containsKey("roles")) {
                try {
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) attributes.get("roles");
                    log.debug("Updating roles for user {}: {}", userId, roles);

                    // Get user resource and roles
                    var userRolesResource = userResource.roles();
                    
                    // Get current assigned realm roles
                    var currentRoles = userRolesResource.realmLevel().listEffective();
                    log.debug("Current effective roles for user {}: {}", userId, 
                            currentRoles.stream().map(RoleRepresentation::getName).collect(Collectors.toList()));
                    
                    // Get all available realm roles 
                    var availableRoles = getRealmResource().roles().list();
                    Map<String, RoleRepresentation> availableRolesMap = availableRoles.stream()
                        .collect(Collectors.toMap(
                            RoleRepresentation::getName,
                            role -> role,
                            (r1, r2) -> r1  // In case of duplicate names, keep the first one
                        ));
                    
                    // Ensure roles exist in realm
                    for (String roleName : roles) {
                        if (!availableRolesMap.containsKey(roleName)) {
                            log.warn("Role not found in realm: {}", roleName);
                        }
                    }

                    // Filter for existing roles only
                    List<RoleRepresentation> rolesToAdd = roles.stream()
                        .filter(availableRolesMap::containsKey)
                        .map(availableRolesMap::get)
                        .collect(Collectors.toList());
                    
                    if (!rolesToAdd.isEmpty()) {
                        log.debug("Removing all current roles for user: {}", userId);
                        // Remove all current roles
                        userRolesResource.realmLevel().remove(currentRoles);
                        
                        log.debug("Adding roles for user {}: {}", userId, 
                                rolesToAdd.stream().map(RoleRepresentation::getName).collect(Collectors.toList()));
                        // Add new roles
                        userRolesResource.realmLevel().add(rolesToAdd);
                    } else {
                        log.warn("No valid roles found to add for user: {}", userId);
                    }
                } catch (Exception e) {
                    log.error("Error updating roles for user: {}", userId, e);
                    // Don't throw exception - continue with the update even if roles fail
                }
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
     * Check if Keycloak is properly configured and accessible
     */
    public boolean isKeycloakAvailable() {
        try {
            getRealmResource().toRepresentation();
            return true;
        } catch (Exception e) {
            log.error("Error connecting to Keycloak", e);
            return false;
        }
    }

    /**
     * Get available client roles for our client
     */
    public List<String> getAvailableClientRoles() {
        try {
            return getRealmResource().clients().findByClientId(clientId)
                    .stream()
                    .findFirst()
                    .map(client -> getRealmResource().clients().get(client.getId()).roles().list()
                            .stream()
                            .map(RoleRepresentation::getName)
                            .toList())
                    .orElse(Collections.emptyList());
        } catch (Exception e) {
            log.error("Error fetching client roles from Keycloak", e);
            return Collections.emptyList();
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

    public List<GroupRepresentation> getAllFederations() {
        return getRealmResource().groups().groups();
    }
    
    public Optional<GroupRepresentation> getFederationById(String federationId) {
        try {
            GroupRepresentation group = getRealmResource().groups().group(federationId).toRepresentation();
            return Optional.of(group);
        } catch (Exception e) {
            log.error("Error fetching federation", e);
            return Optional.empty();
        }
    }
    
    /**
     * Create a federation from a GroupRepresentation
     */
    public String createFederation(GroupRepresentation group) {
        try {
            Response response = getRealmResource().groups().add(group);
            if (response.getStatus() == 201) {
                String locationPath = response.getLocation().getPath();
                String federationId = locationPath.substring(locationPath.lastIndexOf('/') + 1);
                log.info("Created federation with ID: {}", federationId);
                return federationId;
            } else {
                log.error("Failed to create federation. Status: {}", response.getStatus());
            }
        } catch (Exception e) {
            log.error("Error creating federation", e);
        }
        return null;
    }
    
    /**
     * Create a federation with name and description
     */
    public String createFederation(String name, String description) {
        GroupRepresentation group = new GroupRepresentation();
        group.setName(name);
        
        // Set attributes for the description
        Map<String, List<String>> attributes = new HashMap<>();
        if (description != null && !description.isEmpty()) {
            attributes.put("description", Collections.singletonList(description));
            group.setAttributes(attributes);
        }
        
        return createFederation(group);
    }
    
    /**
     * Update an existing federation
     */
    public boolean updateFederation(String federationId, GroupRepresentation updatedGroup) {
        try {
            // First get the current group to ensure it exists
            GroupResource groupResource = getRealmResource().groups().group(federationId);
            GroupRepresentation currentGroup = groupResource.toRepresentation();
            
            // We want to preserve the ID when updating
            updatedGroup.setId(federationId);
            
            // For subgroups, preserve the existing ones if not specified in the update
            if (updatedGroup.getSubGroups() == null && currentGroup.getSubGroups() != null) {
                updatedGroup.setSubGroups(currentGroup.getSubGroups());
            }
            
            // Update the group
            groupResource.update(updatedGroup);
            log.info("Updated federation with ID: {}", federationId);
            return true;
        } catch (Exception e) {
            log.error("Error updating federation with ID: {}", federationId, e);
            return false;
        }
    }
    
    /**
     * Delete a federation
     */
    public boolean deleteFederation(String federationId) {
        try {
            // Check if the federation exists
            GroupResource groupResource = getRealmResource().groups().group(federationId);
            GroupRepresentation group = groupResource.toRepresentation();
            
            if (group != null) {
                // Delete the federation
                groupResource.remove();
                log.info("Deleted federation with ID: {}", federationId);
                return true;
            } else {
                log.warn("Federation with ID {} not found for deletion", federationId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error deleting federation with ID: {}", federationId, e);
            return false;
        }
    }

    /**
     * Check if a user is a member of a specific federation (group)
     */
    public boolean isUserInFederation(String userId, String federationId) {
        try {
            UserResource userResource = getRealmResource().users().get(userId);
            List<GroupRepresentation> userGroups = userResource.groups();
            
            // Check if any of the user's groups matches the federation ID
            return userGroups.stream()
                .anyMatch(group -> group.getId().equals(federationId));
        } catch (Exception e) {
            log.error("Error checking if user {} is in federation {}", userId, federationId, e);
            return false;
        }
    }

    /**
     * Adds a user to a federation
     *
     * @param userId the user ID
     * @param federationId the federation ID
     * @return true if user was added to federation successfully, false otherwise
     */
    public boolean addUserToFederation(String userId, String federationId) {
        try {
            // Check if user is already in the federation
            if (isUserInFederation(userId, federationId)) {
                log.info("User {} is already in federation {}", userId, federationId);
                return true;
            }
            
            // Add user to federation group
            UserResource userResource = getRealmResource().users().get(userId);
            userResource.joinGroup(federationId);
            
            log.info("Added user {} to federation {}", userId, federationId);
            return true;
        } catch (Exception e) {
            log.error("Error adding user {} to federation {}", userId, federationId, e);
            return false;
        }
    }
    
    
    /**
     * Make a user a federation admin
     */
    public boolean makeFederationAdmin(String userId, String federationId) {
        try {
            // First ensure the user is a member of the federation
            if (!isUserInFederation(userId, federationId)) {
                addUserToFederation(userId, federationId);
            }
            
            // Then assign the FEDERATION_ADMIN role
            List<String> currentRoles = getUserRoles(userId);
            if (!currentRoles.contains("FEDERATION_ADMIN")) {
                assignRoleToUser(userId, "FEDERATION_ADMIN");
            }
            
            // Store the federation admin relationship in user attributes
            UserResource userResource = getRealmResource().users().get(userId);
            UserRepresentation user = userResource.toRepresentation();
            
            Map<String, List<String>> attributes = user.getAttributes();
            if (attributes == null) {
                attributes = new HashMap<>();
            }
            
            // Get or create admin_federations attribute
            String ADMIN_FEDERATIONS_ATTR = "admin_federations";
            List<String> adminFederations = attributes.containsKey(ADMIN_FEDERATIONS_ATTR) 
                ? new ArrayList<>(attributes.get(ADMIN_FEDERATIONS_ATTR)) 
                : new ArrayList<>();
            
            // Add this federation if not already present
            if (!adminFederations.contains(federationId)) {
                adminFederations.add(federationId);
                attributes.put(ADMIN_FEDERATIONS_ATTR, adminFederations);
                user.setAttributes(attributes);
                userResource.update(user);
            }
            
            log.info("User {} made admin of federation {}", userId, federationId);
            return true;
        } catch (Exception e) {
            log.error("Error making user {} admin of federation {}", userId, federationId, e);
            return false;
        }
    }
    
    /**
     * Remove federation admin status from a user
     */
    public boolean removeFederationAdmin(String userId, String federationId) {
        try {
            UserResource userResource = getRealmResource().users().get(userId);
            UserRepresentation user = userResource.toRepresentation();
            
            Map<String, List<String>> attributes = user.getAttributes();
            if (attributes == null || !attributes.containsKey("admin_federations")) {
                return true; // Not an admin, so removal successful
            }
            
            // Remove this federation from admin federations
            String ADMIN_FEDERATIONS_ATTR = "admin_federations";
            List<String> adminFederations = new ArrayList<>(attributes.get(ADMIN_FEDERATIONS_ATTR));
            adminFederations.remove(federationId);
            
            attributes.put(ADMIN_FEDERATIONS_ATTR, adminFederations);
            user.setAttributes(attributes);
            userResource.update(user);
            
            // If no longer admin of any federation, remove the FEDERATION_ADMIN role
            if (adminFederations.isEmpty() && !hasGlobalAdminRole(userId)) {
                RoleRepresentation federationAdminRole = getRealmResource().roles()
                        .get("FEDERATION_ADMIN").toRepresentation();
                userResource.roles().realmLevel().remove(Collections.singletonList(federationAdminRole));
            }
            
            log.info("Removed admin status for user {} from federation {}", userId, federationId);
            return true;
        } catch (Exception e) {
            log.error("Error removing admin status for user {} from federation {}", userId, federationId, e);
            return false;
        }
    }
    
    /**
     * Get federations where user is an admin
     */
    public List<String> getUserAdminFederations(String userId) {
        try {
            UserRepresentation user = getRealmResource().users().get(userId).toRepresentation();
            Map<String, List<String>> attributes = user.getAttributes();
            
            if (attributes != null && attributes.containsKey("admin_federations")) {
                return new ArrayList<>(attributes.get("admin_federations"));
            }
            
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error getting admin federations for user {}", userId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Check if user is an admin of a specific federation
     */
    public boolean isUserFederationAdmin(String userId, String federationId) {
        // Global admins are implicitly federation admins
        if (hasGlobalAdminRole(userId)) {
            return true;
        }
        
        return getUserAdminFederations(userId).contains(federationId);
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
    public boolean assignRoleToUser(String userId, String roleName) {
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
     * Get users who are members of a specific federation (group)
     */
    public List<UserRepresentation> getUsersInFederation(String federationId) {
        try {
            // Get the federation (group) resource
            GroupResource groupResource = getRealmResource().groups().group(federationId);
            
            // Fetch all members of the group
            // Note: The first parameter is 'first' (starting index), the second is 'max' (maximum results)
            // Using 0 and Integer.MAX_VALUE to get all members without pagination
            List<UserRepresentation> members = groupResource.members();
            
            log.debug("Found {} users in federation {}", members.size(), federationId);
            return members;
        } catch (Exception e) {
            log.error("Error getting users in federation {}", federationId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Remove a user from a federation (group)
     */
    public boolean removeUserFromFederation(String userId, String federationId) {
        try {
            // Check if user is actually in the federation
            if (!isUserInFederation(userId, federationId)) {
                log.info("User {} is not in federation {}, nothing to remove", userId, federationId);
                return true; // Not an error since the end state is what was desired
            }
            
            // Get the user resource
            UserResource userResource = getRealmResource().users().get(userId);
            
            // Remove the user from the group
            userResource.leaveGroup(federationId);
            
            // Verify removal was successful
            boolean stillInFederation = isUserInFederation(userId, federationId);
            if (stillInFederation) {
                log.warn("Failed to remove user {} from federation {} - user is still a member", userId, federationId);
                return false;
            }
            
            log.info("Successfully removed user {} from federation {}", userId, federationId);
            return true;
        } catch (Exception e) {
            log.error("Error removing user {} from federation {}: {}", userId, federationId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get all federations (groups) a user belongs to
     */
    public List<String> getUserFederations(String userId) {
        try {
            // Get the user resource
            UserResource userResource = getRealmResource().users().get(userId);
            
            // Get all groups the user belongs to
            List<GroupRepresentation> userGroups = userResource.groups();
            
            // Extract the federation IDs (group IDs)
            List<String> federationIds = userGroups.stream()
                .map(GroupRepresentation::getId)
                .collect(Collectors.toList());
            
            log.debug("User {} belongs to {} federations", userId, federationIds.size());
            return federationIds;
        } catch (Exception e) {
            log.error("Error retrieving federations for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get all federations a user belongs to as GroupRepresentations
     */
    public List<GroupRepresentation> getUserFederationGroups(String userId) {
        try {
            // Get the user resource
            UserResource userResource = getRealmResource().users().get(userId);
            
            // Get all groups the user belongs to
            List<GroupRepresentation> userGroups = userResource.groups();
            
            log.debug("User {} belongs to {} federation groups", userId, userGroups.size());
            return userGroups;
        } catch (Exception e) {
            log.error("Error retrieving federation groups for user {}: {}", userId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get all federations a user belongs to as FederationDTOs
     * (This would use the FederationMapper, so we'd need to inject that dependency)
     */
    public List<FederationDTO> getUserFederationDTOs(String userId) {
        try {
            List<GroupRepresentation> federationGroups = getUserFederationGroups(userId);
            
            // Here you would use the federationMapper to convert these to DTOs
            // This is just a placeholder for structure
            return federationGroups.stream()
                .map(federationMapper::toDto)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error retrieving federation DTOs for user {}: {}", userId, e.getMessage(), e);
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
     * Assigns roles to a user
     *
     * @param userId the user ID
     * @param roles the roles to assign
     * @return true if roles were assigned successfully, false otherwise
     */
    private boolean assignRolesToUser(String userId, Set<String> roles) {
        try {
            log.debug("Attempting to assign roles: {} to user: {}", roles, userId);
            List<RoleRepresentation> realmRoles = new ArrayList<>();

            for (String roleName : roles) {
                roleName = roleName.toLowerCase();
                RoleRepresentation role = getRealmResource().roles().get(roleName).toRepresentation();
                if (role != null) {
                    realmRoles.add(role);
                    log.debug("Role found and added: {}", roleName);
                } else {
                    log.warn("Role not found: {}", roleName);
                }
            }

            if (!realmRoles.isEmpty()) {
                getRealmResource().users().get(userId).roles().realmLevel().add(realmRoles);
                log.debug("Roles successfully assigned to user: {}", userId);
                return true;
            } else {
                log.warn("No valid roles found to assign to user: {}", userId);
                return false;
            }
        } catch (Exception e) {
            log.error("Error assigning roles to user: {}", userId, e);
            return false;
        }
    }

}