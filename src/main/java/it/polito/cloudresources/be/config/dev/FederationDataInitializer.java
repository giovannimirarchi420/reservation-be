package it.polito.cloudresources.be.config.dev;

import java.util.List;

import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import it.polito.cloudresources.be.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
public class FederationDataInitializer {
    private final KeycloakService keycloakService;
    
    @Bean
    public CommandLineRunner initFederations() {
        return _ -> {
            log.info("Initializing sample federations...");
            
            // Ensure roles exist
            keycloakService.ensureRealmRoles("GLOBAL_ADMIN", "FEDERATION_ADMIN", "USER");
            
            // Create sample federations
            String poliToId = keycloakService.createFederation("Politecnico di Torino", "Turin Technical University");
            String uniRomaId = keycloakService.createFederation("Università di Roma", "Rome University");
            
            // Get admin user
            String adminId = keycloakService.getUserByUsername("admin1")
                    .map(UserRepresentation::getId)
                    .orElseThrow(() -> new RuntimeException("Admin user not found"));
            
            // Add admin to both federations
            keycloakService.addUserToFederation(adminId, poliToId);
            keycloakService.addUserToFederation(adminId, uniRomaId);
            
            // Add GLOBAL_ADMIN role to admin
            keycloakService.assignRoleToUser(adminId, "GLOBAL_ADMIN");
            
            // Create a federation admin
            String fedAdminId = keycloakService.createUser(
                    "fedadmin", 
                    "fedadmin@example.com", 
                    "Federation", 
                    "Admin", 
                    "fedadmin123", 
                    List.of("FEDERATION_ADMIN"));
            
            // Add federation admin to PoliTo
            keycloakService.addUserToFederation(fedAdminId, poliToId);
            
            log.info("Sample federations initialized.");
        };
    }
}