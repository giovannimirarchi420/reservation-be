package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.users.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock implementation of the KeycloakService for development without an actual Keycloak server
 * Updated to support site functionality
 */
@Service
@Profile("dev")
@Slf4j
public class MockKeycloakService extends KeycloakService {

    // In-memory storage of mock users
    private final Map<String, UserRepresentation> users = new HashMap<>();
    private final Map<String, List<String>> userRoles = new HashMap<>();
    private final Map<String, Map<String, List<String>>> userAttributes = new HashMap<>();

    // In-memory storage of mock sites (groups)
    private final Map<String, GroupRepresentation> sites = new HashMap<>();
    private final Map<String, Set<String>> siteMembers = new HashMap<>(); // siteId -> Set of userIds
    private final Map<String, Set<String>> userSites = new HashMap<>(); // userId -> Set of siteIds
    private final Map<String, Set<String>> siteAdmins = new HashMap<>(); // siteId -> Set of adminUserIds

    /**
     * Constructor that initializes with some sample users
     */
    public MockKeycloakService() {
        // Create sample admin user
        UserRepresentation adminUser = new UserRepresentation();
        adminUser.setId("admin-id");
        adminUser.setUsername("admin");
        adminUser.setEmail("admin@example.com");
        adminUser.setFirstName("Admin");
        adminUser.setLastName("User");
        adminUser.setEnabled(true);
        adminUser.setEmailVerified(true);

        // Set admin attributes
        Map<String, List<String>> adminAttributes = new HashMap<>();
        adminAttributes.put(ATTR_AVATAR, Collections.singletonList("AU"));
        userAttributes.put(adminUser.getId(), adminAttributes);

        users.put(adminUser.getId(), adminUser);
        userRoles.put(adminUser.getId(), Arrays.asList("ADMIN", "USER", "GLOBAL_ADMIN"));

        // Create sample regular user
        UserRepresentation regularUser = new UserRepresentation();
        regularUser.setId("user-id");
        regularUser.setUsername("user");
        regularUser.setEmail("user@example.com");
        regularUser.setFirstName("Regular");
        regularUser.setLastName("User");
        regularUser.setEnabled(true);
        regularUser.setEmailVerified(true);

        // Set user attributes
        Map<String, List<String>> userAttributes = new HashMap<>();
        userAttributes.put(ATTR_AVATAR, Collections.singletonList("RU"));
        this.userAttributes.put(regularUser.getId(), userAttributes);

        users.put(regularUser.getId(), regularUser);
        userRoles.put(regularUser.getId(), List.of("USER"));

        // Initialize empty sets for user sites
        userSites.put(adminUser.getId(), new HashSet<>());
        userSites.put(regularUser.getId(), new HashSet<>());

        log.info("MockKeycloakService initialized with sample users");
    }

    // Original methods from MockKeycloakService
    @Override
    public List<UserRepresentation> getUsers() {
        return new ArrayList<>(users.values());
    }

    @Override
    public Optional<UserRepresentation> getUserByUsername(String username) {
        return users.values().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public Optional<UserRepresentation> getUserByEmail(String email) {
        return users.values().stream()
                .filter(user -> user.getEmail().equals(email))
                .findFirst();
    }

    @Override
    public Optional<UserRepresentation> getUserById(String id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public String createUser(UserDTO userDTO, String password, Set<String> roles) {
        String userId = UUID.randomUUID().toString();

        UserRepresentation user = new UserRepresentation();
        user.setId(userId);
        user.setUsername(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setFirstName(userDTO.getFirstName());
        user.setLastName(userDTO.getLastName());
        user.setEnabled(true);
        user.setEmailVerified(true);

        // Set attributes
        Map<String, List<String>> attributes = new HashMap<>();
        if (userDTO.getSshPublicKey() != null && !userDTO.getSshPublicKey().isEmpty()) {
            attributes.put(ATTR_SSH_KEY, Collections.singletonList(userDTO.getSshPublicKey()));
        }
        if (userDTO.getAvatar() != null && !userDTO.getAvatar().isEmpty()) {
            attributes.put(ATTR_AVATAR, Collections.singletonList(userDTO.getAvatar()));
        }
        userAttributes.put(userId, attributes);

        users.put(userId, user);
        userRoles.put(userId, roles != null ? new ArrayList<>(roles) : new ArrayList<>());
        userSites.put(userId, new HashSet<>());

        // Add user to site if specified
        if (userDTO.getSiteId() != null && !userDTO.getSiteId().isEmpty()) {
            addUserToKeycloakGroup(userId, userDTO.getSiteId());
        }

        log.info("Created mock user: {}", userDTO.getUsername());
        return userId;
    }

    @Override
    public boolean updateUser(String userId, Map<String, Object> attributes) {
        UserRepresentation user = users.get(userId);
        if (user == null) {
            return false;
        }

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

        // Handle user attributes
        Map<String, List<String>> userAttrs = userAttributes.getOrDefault(userId, new HashMap<>());

        if (attributes.containsKey(ATTR_SSH_KEY)) {
            String sshKey = (String) attributes.get(ATTR_SSH_KEY);
            if (sshKey != null && !sshKey.isEmpty()) {
                userAttrs.put(ATTR_SSH_KEY, Collections.singletonList(sshKey));
            } else {
                userAttrs.remove(ATTR_SSH_KEY);
            }
        }

        if (attributes.containsKey(ATTR_AVATAR)) {
            String avatar = (String) attributes.get(ATTR_AVATAR);
            if (avatar != null && !avatar.isEmpty()) {
                userAttrs.put(ATTR_AVATAR, Collections.singletonList(avatar));
            } else {
                userAttrs.remove(ATTR_AVATAR);
            }
        }

        userAttributes.put(userId, userAttrs);

        if (attributes.containsKey("roles")) {
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) attributes.get("roles");
            userRoles.put(userId, roles != null ? new ArrayList<>(roles) : new ArrayList<>());
        }

        log.info("Updated mock user: {}", user.getUsername());
        return true;
    }

    @Override
    public boolean deleteUser(String userId) {
        if (users.containsKey(userId)) {
            String username = users.get(userId).getUsername();
            users.remove(userId);
            userRoles.remove(userId);
            userAttributes.remove(userId);

            // Remove from sites
            Set<String> siteIds = userSites.getOrDefault(userId, new HashSet<>());
            for (String fedId : siteIds) {
                Set<String> members = siteMembers.getOrDefault(fedId, new HashSet<>());
                members.remove(userId);
                siteMembers.put(fedId, members);

                // Remove from federation admins if applicable
                Set<String> admins = siteAdmins.getOrDefault(fedId, new HashSet<>());
                admins.remove(userId);
                siteAdmins.put(fedId, admins);
            }
            userSites.remove(userId);

            log.info("Deleted mock user: {}", username);
            return true;
        }
        return false;
    }

    @Override
    public List<String> getUserRoles(String userId) {
        return userRoles.getOrDefault(userId, new ArrayList<>());
    }

    @Override
    public Optional<String> getUserAttribute(String userId, String attributeName) {
        Map<String, List<String>> attrs = userAttributes.get(userId);
        if (attrs != null && attrs.containsKey(attributeName)) {
            List<String> values = attrs.get(attributeName);
            if (values != null && !values.isEmpty()) {
                return Optional.of(values.get(0));
            }
        }
        return Optional.empty();
    }

    @Override
    public void ensureRealmRoles(String... roleNames) {
        log.info("Ensuring mock realm roles: {}", Arrays.toString(roleNames));
        // No-op in mock implementation
    }

    @Override
    public boolean isKeycloakAvailable() {
        // Mock is always available
        return true;
    }

    @Override
    public List<String> getAvailableClientRoles() {
        // Return basic roles
        return Arrays.asList("USER", "ADMIN", "GLOBAL_ADMIN", "FEDERATION_ADMIN");
    }

    @Override
    public List<UserRepresentation> getUsersByRole(String roleName) {
        List<UserRepresentation> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : userRoles.entrySet()) {
            if (entry.getValue().contains(roleName) ||
                    entry.getValue().contains(roleName.toUpperCase()) ||
                    entry.getValue().contains(roleName.toLowerCase())) {
                result.add(users.get(entry.getKey()));
            }
        }
        return result;
    }

    // New methods to support federations

    @Override
    public List<GroupRepresentation> getAllGroups() {
        return new ArrayList<>(sites.values());
    }

    @Override
    public Optional<GroupRepresentation> getGroupById(String id) {
        return Optional.ofNullable(sites.get(id));
    }

    @Override
    public String setupNewKeycloakGroup(String name, String description) {
        String fedId = UUID.randomUUID().toString();

        GroupRepresentation group = new GroupRepresentation();
        group.setId(fedId);
        group.setName(name);

        // Set attributes for the description
        Map<String, List<String>> attributes = new HashMap<>();
        if (description != null && !description.isEmpty()) {
            attributes.put("description", Collections.singletonList(description));
            group.setAttributes(attributes);
        }

        sites.put(fedId, group);
        siteMembers.put(fedId, new HashSet<>());
        siteAdmins.put(fedId, new HashSet<>());

        log.info("Created mock federation: {} with ID: {}", name, fedId);
        return fedId;
    }

    @Override
    public boolean updateGroup(String fedId, GroupRepresentation updatedGroup) {
        if (!sites.containsKey(fedId)) {
            return false;
        }

        GroupRepresentation existingGroup = sites.get(fedId);

        // Update only what is provided
        if (updatedGroup.getName() != null) {
            existingGroup.setName(updatedGroup.getName());
        }

        if (updatedGroup.getAttributes() != null) {
            existingGroup.setAttributes(updatedGroup.getAttributes());
        }

        sites.put(fedId, existingGroup);
        log.info("Updated mock federation: {} with ID: {}", existingGroup.getName(), fedId);
        return true;
    }

    @Override
    public boolean deleteGroup(String fedId) {
        if (!sites.containsKey(fedId)) {
            return false;
        }

        // Remove federation
        String fedName = sites.get(fedId).getName();
        sites.remove(fedId);

        // Remove all users from this federation
        Set<String> members = siteMembers.getOrDefault(fedId, new HashSet<>());
        for (String userId : members) {
            Set<String> userFeds = userSites.getOrDefault(userId, new HashSet<>());
            userFeds.remove(fedId);
            userSites.put(userId, userFeds);
        }

        siteMembers.remove(fedId);
        siteAdmins.remove(fedId);

        log.info("Deleted mock federation: {} with ID: {}", fedName, fedId);
        return true;
    }

    @Override
    public boolean isUserInGroup(String userId, String fedId) {
        Set<String> members = siteMembers.getOrDefault(fedId, new HashSet<>());
        return members.contains(userId);
    }

    @Override
    public boolean addUserToKeycloakGroup(String userId, String groupId) {
        // Check if federation and user exist
        if (!sites.containsKey(groupId) || !users.containsKey(userId)) {
            return false;
        }

        // Add user to federation
        Set<String> members = siteMembers.getOrDefault(groupId, new HashSet<>());
        members.add(userId);
        siteMembers.put(groupId, members);

        // Add federation to user
        Set<String> userFeds = userSites.getOrDefault(userId, new HashSet<>());
        userFeds.add(groupId);
        userSites.put(userId, userFeds);

        log.info("Added user {} to federation {}", userId, groupId);
        return true;
    }

    @Override
    public boolean removeUserFromSite(String userId, String fedId) {
        // Check if user is in federation
        if (!isUserInGroup(userId, fedId)) {
            return true; // Already not in federation
        }

        // Remove user from federation
        Set<String> members = siteMembers.getOrDefault(fedId, new HashSet<>());
        members.remove(userId);
        siteMembers.put(fedId, members);

        // Remove federation from user
        Set<String> userFeds = userSites.getOrDefault(userId, new HashSet<>());
        userFeds.remove(fedId);
        userSites.put(userId, userFeds);

        // If user was admin of this federation, remove that too
        Set<String> admins = siteAdmins.getOrDefault(fedId, new HashSet<>());
        admins.remove(userId);
        siteAdmins.put(fedId, admins);

        log.info("Removed user {} from federation {}", userId, fedId);
        return true;
    }

    @Override
    public List<UserRepresentation> getUsersInGroup(String fedId) {
        Set<String> members = siteMembers.getOrDefault(fedId, new HashSet<>());
        return members.stream()
                .map(users::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getUserSites(String userId) {
        return new ArrayList<>(userSites.getOrDefault(userId, new HashSet<>()));
    }

    @Override
    public List<GroupRepresentation> getUserGroups(String userId) {
        Set<String> federationIds = userSites.getOrDefault(userId, new HashSet<>());
        return federationIds.stream()
                .map(sites::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasGlobalAdminRole(String userId) {
        List<String> roles = getUserRoles(userId);
        return roles.contains("GLOBAL_ADMIN");
    }

    @Override
    public boolean makeFederationAdmin(String userId, String fedId) {
        // Add user to federation if not already a member
        if (!isUserInGroup(userId, fedId)) {
            addUserToKeycloakGroup(userId, fedId);
        }

        // Add FEDERATION_ADMIN role if not already assigned
        List<String> roles = getUserRoles(userId);
        if (!roles.contains("FEDERATION_ADMIN")) {
            roles.add("FEDERATION_ADMIN");
            userRoles.put(userId, roles);
        }

        // Add user to federation admins
        Set<String> admins = siteAdmins.getOrDefault(fedId, new HashSet<>());
        admins.add(userId);
        siteAdmins.put(fedId, admins);

        // Add to user attributes
        Map<String, List<String>> attributes = userAttributes.getOrDefault(userId, new HashMap<>());
        List<String> adminSites = attributes.getOrDefault("admin_federations", new ArrayList<>());
        if (!adminSites.contains(fedId)) {
            adminSites.add(fedId);
        }
        attributes.put("admin_federations", adminSites);
        userAttributes.put(userId, attributes);

        log.info("Made user {} admin of federation {}", userId, fedId);
        return true;
    }

    @Override
    public boolean removeFederationAdmin(String userId, String fedId) {
        // Remove from federation admins
        Set<String> admins = siteAdmins.getOrDefault(fedId, new HashSet<>());
        admins.remove(userId);
        siteAdmins.put(fedId, admins);

        // Update user attributes
        Map<String, List<String>> attributes = userAttributes.getOrDefault(userId, new HashMap<>());
        List<String> adminSites = attributes.getOrDefault("admin_federations", new ArrayList<>());
        adminSites.remove(fedId);
        attributes.put("admin_federations", adminSites);
        userAttributes.put(userId, attributes);

        // If no longer admin of any site and not global admin, remove FEDERATION_ADMIN role
        if (adminSites.isEmpty() && !hasGlobalAdminRole(userId)) {
            List<String> roles = getUserRoles(userId);
            roles.remove("FEDERATION_ADMIN");
            userRoles.put(userId, roles);
        }

        log.info("Removed admin status from user {} for federation {}", userId, fedId);
        return true;
    }

    @Override
    public boolean isUserSiteAdmin(String userId, String fedId) {
        // Global admins are implicitly site admins
        if (hasGlobalAdminRole(userId)) {
            return true;
        }

        // Check if user is explicitly an admin of this site
        Set<String> admins = siteAdmins.getOrDefault(fedId, new HashSet<>());
        return admins.contains(userId);
    }

    @Override
    public List<String> getUserAdminGroups(String userId) {
        // Global admins can administer all sites
        if (hasGlobalAdminRole(userId)) {
            return new ArrayList<>(sites.keySet());
        }

        // Get sites where user is explicitly an admin
        Map<String, List<String>> attributes = userAttributes.getOrDefault(userId, new HashMap<>());
        List<String> adminSites = attributes.getOrDefault("admin_federations", new ArrayList<>());
        return new ArrayList<>(adminSites);
    }

    @Override
    public boolean assignRoleToUser(String userId, String roleName) {
        if (!users.containsKey(userId)) {
            return false;
        }

        List<String> roles = getUserRoles(userId);
        if (!roles.contains(roleName)) {
            roles.add(roleName);
        }
        userRoles.put(userId, roles);

        log.info("Assigned role {} to user {}", roleName, userId);
        return true;
    }
}