package it.polito.cloudresources.be.config.dev;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import it.polito.cloudresources.be.dto.users.CreateUserDTO;
import it.polito.cloudresources.be.dto.users.UserDTO;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import it.polito.cloudresources.be.service.KeycloakService;
import it.polito.cloudresources.be.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Initializes sites with sample data for development
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
public class SiteDataInitializer {
    private final KeycloakService keycloakService;
    private final ResourceRepository resourceRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final UserService userService;

    @Bean(name = "initSites")
    public CommandLineRunner initSites() {
        return arg -> {
            log.info("Initializing sample sites...");

            // Ensure core roles exist
            keycloakService.ensureRealmRoles("GLOBAL_ADMIN", "USER");

            // Create sample sites
            String poliToId = keycloakService.setupNewKeycloakGroup("polito", "Turin Technical University", false);
            String uniRomaId = keycloakService.setupNewKeycloakGroup("uniroma", "Rome University", false);
            String uniMiId = keycloakService.setupNewKeycloakGroup("unimi", "Milan University", false);

            log.info("Created sites with IDs: {}, {}, {}", poliToId, uniRomaId, uniMiId);

            // Create global admin user if needed
            String adminId = createGlobalAdminUser(poliToId);

            // Add admin to all sites
            keycloakService.addUserToKeycloakGroup(adminId, poliToId);
            keycloakService.addUserToKeycloakGroup(adminId, uniRomaId);
            keycloakService.addUserToKeycloakGroup(adminId, uniMiId);

            // Create a regular user if needed
            String userId = createRegularUser(poliToId);

            // Add regular user to PoliTo only
            keycloakService.addUserToKeycloakGroup(userId, poliToId);

            // Create site admins
            String poliToAdminId = createSiteAdmin("polito_admin", poliToId);
            String uniRomaAdminId = createSiteAdmin("uniroma_admin", uniRomaId);

            // Update resource types and resources to include site IDs
            updateResourceTypes(poliToId, uniRomaId, uniMiId);

            log.info("Sample sites initialized.");
        };
    }

    /**
     * Create global admin user
     */
    private String createGlobalAdminUser(String siteId) {
        // Check if admin already exists
        String password = "admin123";
        Set<String> roles = new HashSet<>(List.of("GLOBAL_ADMIN", "USER"));

        return userService.getUserByUsername("admin1")
                .map(UserDTO::getId)
                .orElseGet(() -> {
                    // Create admin using CreateUserDTO
                    CreateUserDTO adminDTO = CreateUserDTO.builder()
                            .username("admin1")
                            .email("admin@example.com")
                            .firstName("Global")
                            .lastName("Admin")
                            .roles(roles)
                            .avatar("GA")
                            .password(password)
                            .build();

                    try {
                        UserDTO createdAdmin = userService.createUser(adminDTO, password, "admin1");
                        return createdAdmin.getId();
                    } catch (Exception e) {
                        log.error("Error creating admin user: {}", e.getMessage());
                        throw new RuntimeException("Failed to create admin user", e);
                    }
                });
    }

    /**
     * Create regular user
     */
    private String createRegularUser(String siteId) {
        // Check if user already exists
        String password = "user123";
        Set<String> roles = new HashSet<>(List.of("USER"));

        return userService.getUserByUsername("user1")
                .map(UserDTO::getId)
                .orElseGet(() -> {
                    // Create user using CreateUserDTO
                    CreateUserDTO userDTO = CreateUserDTO.builder()
                            .username("user1")
                            .email("user@example.com")
                            .firstName("Regular")
                            .lastName("User")
                            .roles(roles)
                            .avatar("RU")
                            .password(password)
                            .build();

                    try {
                        UserDTO createdUser = userService.createUser(userDTO, password, "admin1");
                        return createdUser.getId();
                    } catch (Exception e) {
                        log.error("Error creating regular user: {}", e.getMessage());
                        throw new RuntimeException("Failed to create regular user", e);
                    }
                });
    }

    /**
     * Create site admin user
     */
    private String createSiteAdmin(String username, String siteId) {
        // Check if admin already exists
        String password = "admin123";
        Set<String> roles = new HashSet<>(List.of("USER"));

        return userService.getUserByUsername(username)
                .map(UserDTO::getId)
                .orElseGet(() -> {
                    // Create site admin using CreateUserDTO with just the USER role initially
                    CreateUserDTO adminDTO = CreateUserDTO.builder()
                            .username(username)
                            .email(username + "@example.com")
                            .firstName(username.substring(0, 1).toUpperCase() + username.substring(1).split("_")[0])
                            .lastName("Admin")
                            .roles(roles)
                            .avatar(username.substring(0, 1).toUpperCase() + "A")
                            .password(password)
                            .build();

                    try {
                        // Create the user with basic role
                        UserDTO createdAdmin = userService.createUser(adminDTO, password, "admin1");
                        String adminId = createdAdmin.getId();

                        // Get the site name from the ID
                        String siteName = keycloakService.getGroupById(siteId)
                                .map(group -> group.getName())
                                .orElseThrow(() -> new RuntimeException("Site not found with ID: " + siteId));

                        // Now assign the site-specific admin role
                        keycloakService.assignSiteAdminRole(adminId, siteName);

                        log.info("Created site admin {} with role {}_site_admin", username, siteName.toLowerCase());

                        return adminId;
                    } catch (Exception e) {
                        log.error("Error creating site admin user: {}", e.getMessage());
                        throw new RuntimeException("Failed to create site admin user", e);
                    }
                });
    }

    /**
     * Updates existing resource types and resources to include site IDs
     */
    private void updateResourceTypes(String poliToId, String uniRomaId, String uniMiId) {
        List<ResourceType> resourceTypes = resourceTypeRepository.findAll();

        if (!resourceTypes.isEmpty()) {
            // Assign the first resource type to PoliTo, second to UniRoma, rest to Milano
            for (int i = 0; i < resourceTypes.size(); i++) {
                ResourceType resourceType = resourceTypes.get(i);
                String siteId;

                if (i == 0) {
                    siteId = poliToId;
                } else if (i == 1) {
                    siteId = uniRomaId;
                } else {
                    siteId = uniMiId;
                }

                // Update the resource type with site ID
                resourceType.setSiteId(siteId);
                resourceTypeRepository.save(resourceType);

                // Update all resources of this type to match the site
                List<Resource> resources = resourceRepository.findByTypeId(resourceType.getId());
                for (Resource resource : resources) {
                    resource.setSiteId(siteId);
                    resourceRepository.save(resource);
                }

                log.info("Updated resource type {} and {} resources to site {}",
                        resourceType.getName(), resources.size(), siteId);
            }
        }
    }
}