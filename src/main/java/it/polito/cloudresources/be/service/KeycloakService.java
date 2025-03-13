package it.polito.cloudresources.be.service;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for interacting with Keycloak
 */
@Service
@Profile("!dev") // Active in all profiles except dev
@Slf4j
public class KeycloakService {

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
     * Create a new user in Keycloak
     */
    /**
     * Create a new user in Keycloak
     */
    @Transactional
    public String createUser(String username, String email, String firstName, String lastName, String password, List<String> roles) {
        try {
            log.debug("Attempting to create user: username={}, email={}, firstName={}, lastName={}", 
                    username, email, firstName, lastName);
            
            // Create a completely new UserRepresentation object
            UserRepresentation user = new UserRepresentation();
            
            // Set ONLY the basic fields, avoiding any problematic fields
            Map<String, List<String>> attributes = new HashMap<>();
            
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(true);
            user.setAttributes(attributes);            
            // Verify that all necessary fields are present
            if (username == null || username.trim().isEmpty()) {
                log.error("User creation failed: username missing or empty");
                return null;
            }
            if (email == null || email.trim().isEmpty()) {
                log.error("User creation failed: email missing or empty");
                return null;
            }
            
            // Create user
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

            // Set the password
            try {
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(password);
                credential.setTemporary(false);
                
                usersResource.get(userId).resetPassword(credential);
                log.debug("Password successfully set for user: {}", userId);
            } catch (Exception e) {
                log.error("Error setting password for user: {}", userId, e);
                // Don't return null here, the user has been created even if the password hasn't been set
            }

            // Assign roles to the user
            if (roles != null && !roles.isEmpty()) {
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
                    } else {
                        log.warn("No valid roles to assign to user: {}", userId);
                    }
                } catch (Exception e) {
                    log.error("Error assigning roles to user: {}", userId, e);
                    // Don't return null here, the user has been created even if the roles haven't been assigned
                }
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
            UserRepresentation user = getRealmResource().users().get(userId).toRepresentation();

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

            // Update basic user info first
            getRealmResource().users().get(userId).update(user);
            log.debug("Basic user info updated for user: {}", userId);

            // Update password if provided
            if (attributes.containsKey("password")) {
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue((String) attributes.get("password"));
                credential.setTemporary(false);

                getRealmResource().users().get(userId).resetPassword(credential);
                log.debug("Password updated for user: {}", userId);
            }

            // Update roles if provided
            if (attributes.containsKey("roles")) {
                try {
                    @SuppressWarnings("unchecked")
                    List<String> roles = (List<String>) attributes.get("roles");
                    log.debug("Updating roles for user {}: {}", userId, roles);

                    // Get user resource and roles
                    var userResource = getRealmResource().users().get(userId);
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
}