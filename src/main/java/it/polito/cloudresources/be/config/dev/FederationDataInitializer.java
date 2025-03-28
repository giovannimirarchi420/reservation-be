package it.polito.cloudresources.be.config.dev;

import java.util.List;

import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import it.polito.cloudresources.be.service.KeycloakService;
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
    
    @Bean
    public CommandLineRunner initFederations() {
        return arg -> {
            log.info("Initializing sample federations...");
            
            // Ensure roles exist
            keycloakService.ensureRealmRoles("GLOBAL_ADMIN", "FEDERATION_ADMIN", "USER");
            
            // Create sample federations
            String poliToId = keycloakService.createFederation("Politecnico di Torino", "Turin Technical University");
            String uniRomaId = keycloakService.createFederation("Università di Roma", "Rome University");
            String uniMiId = keycloakService.createFederation("Università di Milano", "Milan University");
            
            log.info("Created federations with IDs: {}, {}, {}", poliToId, uniRomaId, uniMiId);
            
            // Get admin user
            String adminId = keycloakService.getUserByUsername("admin1")
                    .map(UserRepresentation::getId)
                    .orElseGet(() -> {
                        // Create admin if doesn't exist
                        return keycloakService.createUser(
                                "admin1", 
                                "admin@example.com", 
                                "Global", 
                                "Admin", 
                                "admin123", 
                                List.of("GLOBAL_ADMIN", "ADMIN", "USER"));
                    });
            
            // Add admin to all federations
            keycloakService.addUserToFederation(adminId, poliToId);
            keycloakService.addUserToFederation(adminId, uniRomaId);
            keycloakService.addUserToFederation(adminId, uniMiId);
            
            // Create a regular user if it doesn't exist
            String userId = keycloakService.getUserByUsername("user1")
                    .map(UserRepresentation::getId)
                    .orElseGet(() -> {
                        // Create user if doesn't exist
                        return keycloakService.createUser(
                                "user1", 
                                "user@example.com", 
                                "Regular", 
                                "User", 
                                "user123", 
                                List.of("USER"));
                    });
            
            // Add regular user to PoliTo only
            keycloakService.addUserToFederation(userId, poliToId);
            
            // Create federation admins
            String poliToAdminId = keycloakService.getUserByUsername("polito_admin")
                    .map(UserRepresentation::getId)
                    .orElseGet(() -> {
                        return keycloakService.createUser(
                                "polito_admin", 
                                "polito_admin@example.com", 
                                "Polito", 
                                "Admin", 
                                "admin123", 
                                List.of("FEDERATION_ADMIN", "USER"));
                    });
            
            String uniRomaAdminId = keycloakService.getUserByUsername("uniroma_admin")
                    .map(UserRepresentation::getId)
                    .orElseGet(() -> {
                        return keycloakService.createUser(
                                "uniroma_admin", 
                                "uniroma_admin@example.com", 
                                "UniRoma", 
                                "Admin", 
                                "admin123", 
                                List.of("FEDERATION_ADMIN", "USER"));
                    });
            
            // Add federation admins to their respective federations
            keycloakService.addUserToFederation(poliToAdminId, poliToId);
            keycloakService.makeFederationAdmin(poliToAdminId, poliToId);
            
            keycloakService.addUserToFederation(uniRomaAdminId, uniRomaId);
            keycloakService.makeFederationAdmin(uniRomaAdminId, uniRomaId);
            
            // Update resource types and resources to include federation IDs
            updateResourceTypes(poliToId, uniRomaId, uniMiId);
            
            log.info("Sample federations initialized.");
        };
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
                
                resourceType.setFederationId(fedId);
                resourceTypeRepository.save(resourceType);
                
                // Update all resources of this type to match the federation
                List<Resource> resources = resourceRepository.findByTypeId(resourceType.getId());
                for (Resource resource : resources) {
                    resource.setFederationId(fedId);
                    resourceRepository.save(resource);
                }
                
                log.info("Updated resource type {} and {} resources to federation {}", 
                    resourceType.getName(), resources.size(), fedId);
            }
        }
    }
}