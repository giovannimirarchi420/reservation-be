package it.polito.cloudresources.be.service;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Mock implementation of the KeycloakService for development without an actual Keycloak server
 * Updated to support federation functionality
 */
@Service
@Profile("dev")
@Slf4j
public class MockKeycloakService extends KeycloakService {

    // In-memory storage of mock users
    private final Map<String, UserRepresentation> users = new HashMap<>();
    private final Map<String, List<String>> userRoles = new HashMap<>();
    private final Map<String, Map<String, List<String>>> userAttributes = new HashMap<>();
    
    // In-memory storage of mock federations (groups)
    private final Map<String, GroupRepresentation> federations = new HashMap<>();
    private final Map<String, Set<String>> federationMembers = new HashMap<>(); // federationId -> Set of userIds
    private final Map<String, Set<String>> userFederations = new HashMap<>(); // userId -> Set of federationIds
    private final Map<String, Set<String>> federationAdmins = new HashMap<>(); // federationId -> Set of adminUserIds

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

        // Initialize empty sets for user federations
        userFederations.put(adminUser.getId(), new HashSet<>());
        userFederations.put(regularUser.getId(), new HashSet<>());

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
    public String createUser(String username, String email, String firstName, String lastName, 
                             String password, List<String> roles, String sshKey, String avatar, String federationId) {
        String userId = UUID.randomUUID().toString();

        UserRepresentation user = new UserRepresentation();
        user.setId(userId);
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);

        // Set attributes
        Map<String, List<String>> attributes = new HashMap<>();
        if (sshKey != null && !sshKey.isEmpty()) {
            attributes.put(ATTR_SSH_KEY, Collections.singletonList(sshKey));
        }
        if (avatar != null && !avatar.isEmpty()) {
            attributes.put(ATTR_AVATAR, Collections.singletonList(avatar));
        }
        userAttributes.put(userId, attributes);
        
        users.put(userId, user);
        userRoles.put(userId, roles != null ? new ArrayList<>(roles) : new ArrayList<>());
        userFederations.put(userId, new HashSet<>());

        log.info("Created mock user: {}", username);
        return userId;
    }

    @Override
    public String createUser(String username, String email, String firstName, String lastName, String password, List<String> roles) {
        return createUser(username, email, firstName, lastName, password, roles, null, null, null);
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

        // Handle admin_federations attribute
        if (attributes.containsKey("admin_federations")) {
            @SuppressWarnings("unchecked")
            List<String> adminFederations = (List<String>) attributes.get("admin_federations");
            if (adminFederations != null) {
                for (String fedId : adminFederations) {
                    Set<String> admins = federationAdmins.getOrDefault(fedId, new HashSet<>());
                    admins.add(userId);
                    federationAdmins.put(fedId, admins);
                }
            }
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
            
            // Remove from federations
            Set<String> federationIds = userFederations.getOrDefault(userId, new HashSet<>());
            for (String fedId : federationIds) {
                Set<String> members = federationMembers.getOrDefault(fedId, new HashSet<>());
                members.remove(userId);
                federationMembers.put(fedId, members);
                
                // Remove from federation admins if applicable
                Set<String> admins = federationAdmins.getOrDefault(fedId, new HashSet<>());
                admins.remove(userId);
                federationAdmins.put(fedId, admins);
            }
            userFederations.remove(userId);
            
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
    public List<GroupRepresentation> getAllFederations() {
        return new ArrayList<>(federations.values());
    }
    
    @Override
    public Optional<GroupRepresentation> getFederationById(String id) {
        return Optional.ofNullable(federations.get(id));
    }
    
    @Override
    public String createFederation(String name, String description) {
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
        
        federations.put(fedId, group);
        federationMembers.put(fedId, new HashSet<>());
        federationAdmins.put(fedId, new HashSet<>());
        
        log.info("Created mock federation: {} with ID: {}", name, fedId);
        return fedId;
    }
    
    @Override
    public boolean updateFederation(String fedId, GroupRepresentation updatedGroup) {
        if (!federations.containsKey(fedId)) {
            return false;
        }
        
        GroupRepresentation existingGroup = federations.get(fedId);
        
        // Update only what is provided
        if (updatedGroup.getName() != null) {
            existingGroup.setName(updatedGroup.getName());
        }
        
        if (updatedGroup.getAttributes() != null) {
            existingGroup.setAttributes(updatedGroup.getAttributes());
        }
        
        federations.put(fedId, existingGroup);
        log.info("Updated mock federation: {} with ID: {}", existingGroup.getName(), fedId);
        return true;
    }
    
    @Override
    public boolean deleteFederation(String fedId) {
        if (!federations.containsKey(fedId)) {
            return false;
        }
        
        // Remove federation
        String fedName = federations.get(fedId).getName();
        federations.remove(fedId);
        
        // Remove all users from this federation
        Set<String> members = federationMembers.getOrDefault(fedId, new HashSet<>());
        for (String userId : members) {
            Set<String> userFeds = userFederations.getOrDefault(userId, new HashSet<>());
            userFeds.remove(fedId);
            userFederations.put(userId, userFeds);
        }
        
        federationMembers.remove(fedId);
        federationAdmins.remove(fedId);
        
        log.info("Deleted mock federation: {} with ID: {}", fedName, fedId);
        return true;
    }
    
    @Override
    public boolean isUserInFederation(String userId, String fedId) {
        Set<String> members = federationMembers.getOrDefault(fedId, new HashSet<>());
        return members.contains(userId);
    }
    
    @Override
    public boolean addUserToFederation(String userId, String fedId) {
        // Check if federation and user exist
        if (!federations.containsKey(fedId) || !users.containsKey(userId)) {
            return false;
        }
        
        // Add user to federation
        Set<String> members = federationMembers.getOrDefault(fedId, new HashSet<>());
        members.add(userId);
        federationMembers.put(fedId, members);
        
        // Add federation to user
        Set<String> userFeds = userFederations.getOrDefault(userId, new HashSet<>());
        userFeds.add(fedId);
        userFederations.put(userId, userFeds);
        
        log.info("Added user {} to federation {}", userId, fedId);
        return true;
    }
    
    @Override
    public boolean removeUserFromFederation(String userId, String fedId) {
        // Check if user is in federation
        if (!isUserInFederation(userId, fedId)) {
            return true; // Already not in federation
        }
        
        // Remove user from federation
        Set<String> members = federationMembers.getOrDefault(fedId, new HashSet<>());
        members.remove(userId);
        federationMembers.put(fedId, members);
        
        // Remove federation from user
        Set<String> userFeds = userFederations.getOrDefault(userId, new HashSet<>());
        userFeds.remove(fedId);
        userFederations.put(userId, userFeds);
        
        // If user was admin of this federation, remove that too
        Set<String> admins = federationAdmins.getOrDefault(fedId, new HashSet<>());
        admins.remove(userId);
        federationAdmins.put(fedId, admins);
        
        log.info("Removed user {} from federation {}", userId, fedId);
        return true;
    }
    
    @Override
    public List<UserRepresentation> getUsersInFederation(String fedId) {
        Set<String> members = federationMembers.getOrDefault(fedId, new HashSet<>());
        return members.stream()
                .map(users::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> getUserFederations(String userId) {
        return new ArrayList<>(userFederations.getOrDefault(userId, new HashSet<>()));
    }
    
    @Override
    public List<GroupRepresentation> getUserFederationGroups(String userId) {
        Set<String> federationIds = userFederations.getOrDefault(userId, new HashSet<>());
        return federationIds.stream()
                .map(federations::get)
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
        if (!isUserInFederation(userId, fedId)) {
            addUserToFederation(userId, fedId);
        }
        
        // Add FEDERATION_ADMIN role if not already assigned
        List<String> roles = getUserRoles(userId);
        if (!roles.contains("FEDERATION_ADMIN")) {
            roles.add("FEDERATION_ADMIN");
            userRoles.put(userId, roles);
        }
        
        // Add user to federation admins
        Set<String> admins = federationAdmins.getOrDefault(fedId, new HashSet<>());
        admins.add(userId);
        federationAdmins.put(fedId, admins);
        
        // Add to user attributes
        Map<String, List<String>> attributes = userAttributes.getOrDefault(userId, new HashMap<>());
        List<String> adminFederations = attributes.getOrDefault("admin_federations", new ArrayList<>());
        if (!adminFederations.contains(fedId)) {
            adminFederations.add(fedId);
        }
        attributes.put("admin_federations", adminFederations);
        userAttributes.put(userId, attributes);
        
        log.info("Made user {} admin of federation {}", userId, fedId);
        return true;
    }
    
    @Override
    public boolean removeFederationAdmin(String userId, String fedId) {
        // Remove from federation admins
        Set<String> admins = federationAdmins.getOrDefault(fedId, new HashSet<>());
        admins.remove(userId);
        federationAdmins.put(fedId, admins);
        
        // Update user attributes
        Map<String, List<String>> attributes = userAttributes.getOrDefault(userId, new HashMap<>());
        List<String> adminFederations = attributes.getOrDefault("admin_federations", new ArrayList<>());
        adminFederations.remove(fedId);
        attributes.put("admin_federations", adminFederations);
        userAttributes.put(userId, attributes);
        
        // If no longer admin of any federation and not global admin, remove FEDERATION_ADMIN role
        if (adminFederations.isEmpty() && !hasGlobalAdminRole(userId)) {
            List<String> roles = getUserRoles(userId);
            roles.remove("FEDERATION_ADMIN");
            userRoles.put(userId, roles);
        }
        
        log.info("Removed admin status from user {} for federation {}", userId, fedId);
        return true;
    }
    
    @Override
    public boolean isUserFederationAdmin(String userId, String fedId) {
        // Global admins are implicitly federation admins
        if (hasGlobalAdminRole(userId)) {
            return true;
        }
        
        // Check if user is explicitly an admin of this federation
        Set<String> admins = federationAdmins.getOrDefault(fedId, new HashSet<>());
        return admins.contains(userId);
    }
    
    @Override
    public List<String> getUserAdminFederations(String userId) {
        // Global admins can administer all federations
        if (hasGlobalAdminRole(userId)) {
            return new ArrayList<>(federations.keySet());
        }
        
        // Get federations where user is explicitly an admin
        Map<String, List<String>> attributes = userAttributes.getOrDefault(userId, new HashMap<>());
        List<String> adminFederations = attributes.getOrDefault("admin_federations", new ArrayList<>());
        return new ArrayList<>(adminFederations);
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