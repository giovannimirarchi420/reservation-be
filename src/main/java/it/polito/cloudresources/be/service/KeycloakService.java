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
            log.debug("Tentativo di creazione utente: username={}, email={}, firstName={}, lastName={}", 
                    username, email, firstName, lastName);
            
            // Crea un oggetto UserRepresentation completamente nuovo
            UserRepresentation user = new UserRepresentation();
            
            // Imposta SOLO i campi base, evitando qualsiasi campo problematico
            Map<String, List<String>> attributes = new HashMap<>();
            
            user.setUsername(username);
            user.setEmail(email);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setEnabled(true);
            user.setEmailVerified(true);
            user.setAttributes(attributes);            
            // Verifica che tutti i campi necessari siano presenti
            if (username == null || username.trim().isEmpty()) {
                log.error("Creazione utente fallita: username mancante o vuoto");
                return null;
            }
            if (email == null || email.trim().isEmpty()) {
                log.error("Creazione utente fallita: email mancante o vuota");
                return null;
            }
            
            // Create user
            UsersResource usersResource = getRealmResource().users();
            
            // Log dettagliato della rappresentazione dell'utente
            log.debug("Rappresentazione utente: {}", user);
            
            Response response = usersResource.create(user);
            log.debug("Risposta creazione utente - Status: {}, Messaggio: {}", 
                    response.getStatus(), response.getStatusInfo().getReasonPhrase());
                    
            if (response.getStatus() != 201) {
                if (response.hasEntity()) {
                    // Prova a leggere il corpo della risposta per capire meglio l'errore
                    String responseBody = response.readEntity(String.class);
                    log.error("Dettaglio errore da Keycloak: {}", responseBody);
                }
                log.error("Creazione utente fallita in Keycloak: {} ({})", 
                        response.getStatusInfo().getReasonPhrase(), response.getStatus());
                return null;
            }

            // Get created user ID
            String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");
            log.info("Utente creato in Keycloak con ID: {}", userId);

            // Imposta la password
            try {
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue(password);
                credential.setTemporary(false);
                
                usersResource.get(userId).resetPassword(credential);
                log.debug("Password impostata con successo per l'utente: {}", userId);
            } catch (Exception e) {
                log.error("Errore durante l'impostazione della password per l'utente: {}", userId, e);
                // Non restituire null qui, l'utente è stato creato anche se la password non è stata impostata
            }

            // Assign roles to the user
            if (roles != null && !roles.isEmpty()) {
                try {
                    log.debug("Tentativo di assegnare i ruoli: {} all'utente: {}", roles, userId);
                    List<RoleRepresentation> realmRoles = new ArrayList<>();

                    for (String roleName : roles) {
                        roleName = roleName.toLowerCase();
                        RoleRepresentation role = getRealmResource().roles().get(roleName).toRepresentation();
                        if (role != null) {
                            realmRoles.add(role);
                            log.debug("Ruolo trovato e aggiunto: {}", roleName);
                        } else {
                            log.warn("Ruolo non trovato: {}", roleName);
                        }
                    }

                    if (!realmRoles.isEmpty()) {
                        getRealmResource().users().get(userId).roles().realmLevel().add(realmRoles);
                        log.debug("Ruoli assegnati con successo all'utente: {}", userId);
                    } else {
                        log.warn("Nessun ruolo valido da assegnare all'utente: {}", userId);
                    }
                } catch (Exception e) {
                    log.error("Errore durante l'assegnazione dei ruoli all'utente: {}", userId, e);
                    // Non restituire null qui, l'utente è stato creato anche se i ruoli non sono stati assegnati
                }
            }

            return userId;
        } catch (Exception e) {
            log.error("Errore durante la creazione dell'utente in Keycloak", e);
            return null;
        }
    }
    /**
     * Update an existing user in Keycloak
     */
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

            if (attributes.containsKey("enabled")) {
                user.setEnabled((Boolean) attributes.get("enabled"));
            }

            getRealmResource().users().get(userId).update(user);

            // Update password if provided
            if (attributes.containsKey("password")) {
                CredentialRepresentation credential = new CredentialRepresentation();
                credential.setType(CredentialRepresentation.PASSWORD);
                credential.setValue((String) attributes.get("password"));
                credential.setTemporary(false);

                getRealmResource().users().get(userId).resetPassword(credential);
            }

            // Update roles if provided
            if (attributes.containsKey("roles")) {
                @SuppressWarnings("unchecked")
                List<String> roles = (List<String>) attributes.get("roles");

                // Remove existing roles
                List<RoleRepresentation> currentRoles = getRealmResource().users().get(userId).roles().realmLevel().listAll();
                if (!currentRoles.isEmpty()) {
                    getRealmResource().users().get(userId).roles().realmLevel().remove(currentRoles);
                }

                // Add new roles
                if (roles != null && !roles.isEmpty()) {
                    List<RoleRepresentation> newRoles = new ArrayList<>();

                    for (String roleName : roles) {
                        RoleRepresentation role = getRealmResource().roles().get(roleName).toRepresentation();
                        if (role != null) {
                            newRoles.add(role);
                        }
                    }

                    getRealmResource().users().get(userId).roles().realmLevel().add(newRoles);
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