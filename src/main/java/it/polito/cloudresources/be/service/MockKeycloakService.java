package it.polito.cloudresources.be.service;

import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Mock implementation of the KeycloakService for development without an actual Keycloak server
 */
@Service
@Profile("dev")
@Slf4j
public class MockKeycloakService extends KeycloakService {

    // In-memory storage of mock users
    private final Map<String, UserRepresentation> users = new HashMap<>();
    private final Map<String, List<String>> userRoles = new HashMap<>();

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

        users.put(adminUser.getId(), adminUser);
        userRoles.put(adminUser.getId(), Arrays.asList("ADMIN", "USER"));

        // Create sample regular user
        UserRepresentation regularUser = new UserRepresentation();
        regularUser.setId("user-id");
        regularUser.setUsername("user");
        regularUser.setEmail("user@example.com");
        regularUser.setFirstName("Regular");
        regularUser.setLastName("User");
        regularUser.setEnabled(true);
        regularUser.setEmailVerified(true);

        users.put(regularUser.getId(), regularUser);
        userRoles.put(regularUser.getId(), Arrays.asList("USER"));

        log.info("MockKeycloakService initialized with sample users");
    }

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
    public String createUser(String username, String email, String firstName, String lastName, String password, List<String> roles) {
        String userId = UUID.randomUUID().toString();

        UserRepresentation user = new UserRepresentation();
        user.setId(userId);
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(true);

        users.put(userId, user);
        userRoles.put(userId, roles != null ? new ArrayList<>(roles) : new ArrayList<>());

        log.info("Created mock user: {}", username);
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

        if (attributes.containsKey("enabled")) {
            user.setEnabled((Boolean) attributes.get("enabled"));
        }

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
        return Arrays.asList("USER", "ADMIN");
    }
}
