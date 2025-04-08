package it.polito.cloudresources.be.config.dev;

import java.util.HashSet;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import it.polito.cloudresources.be.dto.users.UserDTO;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
public class FederationDataInitializer {
    private final KeycloakService keycloakService;
    private final ResourceRepository resourceRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final UserService userService;

    @Bean
    public CommandLineRunner initFederations() {
        return arg -> {
            log.info("Initializing sample federations...");

            // Ensure roles exist
            keycloakService.ensureRealmRoles("GLOBAL_ADMIN", "FEDERATION_ADMIN", "USER");

            // Create sample federations
            String poliToId = keycloakService.setupNewKeycloakGroup("polito", "Turin Technical University");
            String uniRomaId = keycloakService.setupNewKeycloakGroup("uniroma", "Rome University");
            String uniMiId = keycloakService.setupNewKeycloakGroup("unimi", "Milan University");

            log.info("Created federations with IDs: {}, {}, {}", poliToId, uniRomaId, uniMiId);

            // Create global admin user if needed
            String adminId = createAdminUser(poliToId);

            // Add admin to all federations
            keycloakService.addUserToKeycloakGroup(adminId, poliToId);
            keycloakService.addUserToKeycloakGroup(adminId, uniRomaId);
            keycloakService.addUserToKeycloakGroup(adminId, uniMiId);

            // Create a regular user if needed
            String userId = createRegularUser(poliToId);

            // Add regular user to PoliTo only
            keycloakService.addUserToKeycloakGroup(userId, poliToId);

            // Create federation admins
            String poliToAdminId = createFederationAdmin("polito_admin", poliToId);
            String uniRomaAdminId = createFederationAdmin("uniroma_admin", uniRomaId);

            // Update resource types and resources to include federation IDs
            updateResourceTypes(poliToId, uniRomaId, uniMiId);

            log.info("Sample federations initialized.");
        };
    }

    /**
     * Create global admin user
     */
    private String createAdminUser(String federationId) {
        // Check if admin already exists
        return userService.getUserByUsername("admin1")
                .map(UserDTO::getId)
                .orElseGet(() -> {
                    // Create admin using UserDTO
                    UserDTO adminDTO = UserDTO.builder()
                            .username("admin1")
                            .email("admin@example.com")
                            .firstName("Global")
                            .lastName("Admin")
                            .roles(new HashSet<>(List.of("GLOBAL_ADMIN", "ADMIN", "USER")))
                            .avatar("GA")
                            .siteId(federationId)
                            .build();

                    UserDTO createdAdmin = userService.createUser(adminDTO, "admin123");
                    return createdAdmin.getId();
                });
    }

    /**
     * Create regular user
     */
    private String createRegularUser(String federationId) {
        // Check if user already exists
        return userService.getUserByUsername("user1")
                .map(UserDTO::getId)
                .orElseGet(() -> {
                    // Create user using UserDTO
                    UserDTO userDTO = UserDTO.builder()
                            .username("user1")
                            .email("user@example.com")
                            .firstName("Regular")
                            .lastName("User")
                            .roles(new HashSet<>(List.of("USER")))
                            .avatar("RU")
                            .siteId(federationId)
                            .build();

                    UserDTO createdUser = userService.createUser(userDTO, "user123");
                    return createdUser.getId();
                });
    }

    /**
     * Create federation admin user
     */
    private String createFederationAdmin(String username, String siteId) {
        // Check if admin already exists
        return userService.getUserByUsername(username)
                .map(UserDTO::getId)
                .orElseGet(() -> {
                    // Create federation admin using UserDTO
                    UserDTO adminDTO = UserDTO.builder()
                            .username(username)
                            .email(username + "@example.com")
                            .firstName(username.substring(0, 1).toUpperCase() + username.substring(1).split("_")[0])
                            .lastName("Admin")
                            .roles(new HashSet<>(List.of("FEDERATION_ADMIN", "USER")))
                            .avatar(username.substring(0, 1).toUpperCase() + "A")
                            .siteId(siteId)
                            .build();

                    UserDTO createdAdmin = userService.createUser(adminDTO, "admin123");
                    String adminId = createdAdmin.getId();

                    // Make the user a federation admin
                    keycloakService.assignSiteAdminRole(adminId, siteId);

                    return adminId;
                });
    }

    /**
     * Updates existing resource types and resources to include federation IDs
     */
    private void updateResourceTypes(String poliToId, String uniRomaId, String uniMiId) {
        List<ResourceType> resourceTypes = resourceTypeRepository.findAll();

        if (!resourceTypes.isEmpty()) {
            // Assign the first resource type to PoliTo, second to UniRoma, rest to Milano
            for (int i = 0; i < resourceTypes.size(); i++) {
                ResourceType resourceType = resourceTypes.get(i);
                String fedId;

                if (i == 0) {
                    fedId = poliToId;
                } else if (i == 1) {
                    fedId = uniRomaId;
                } else {
                    fedId = uniMiId;
                }

                resourceType.setSiteId(fedId);
                resourceTypeRepository.save(resourceType);

                // Update all resources of this type to match the federation
                List<Resource> resources = resourceRepository.findByTypeId(resourceType.getId());
                for (Resource resource : resources) {
                    resource.setSiteId(fedId);
                    resourceRepository.save(resource);
                }

                log.info("Updated resource type {} and {} resources to federation {}",
                        resourceType.getName(), resources.size(), fedId);
            }
        }
    }
}