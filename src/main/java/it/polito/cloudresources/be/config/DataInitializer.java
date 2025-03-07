package it.polito.cloudresources.be.config;

import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.model.User;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import it.polito.cloudresources.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Set;

/**
 * Configuration for initializing sample data for development
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("dev") // Only run in development profile
public class DataInitializer {

    private final ResourceTypeRepository resourceTypeRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;

    /**
     * Initialize sample data
     */
    @Bean
    public CommandLineRunner initData() {
        return args -> {
            log.info("Initializing sample data...");
            
            // Create resource types if none exist
            if (resourceTypeRepository.count() == 0) {
                createResourceTypes();
            }
            
            // Create sample resources if none exist
            if (resourceRepository.count() == 0) {
                createSampleResources();
            }
            
            // Create sample admin user if none exist
            if (userRepository.count() == 0) {
                createSampleUser();
            }
            
            log.info("Sample data initialization complete.");
        };
    }

    /**
     * Create resource types
     */
    private void createResourceTypes() {
        log.info("Creating resource types...");
        
        ResourceType serverType = new ResourceType();
        serverType.setName("Server");
        serverType.setColor("#1976d2");
        resourceTypeRepository.save(serverType);
        
        ResourceType gpuType = new ResourceType();
        gpuType.setName("GPU");
        gpuType.setColor("#4caf50");
        resourceTypeRepository.save(gpuType);
        
        ResourceType switchType = new ResourceType();
        switchType.setName("Switch P4");
        switchType.setColor("#ff9800");
        resourceTypeRepository.save(switchType);
        
        log.info("Resource types created.");
    }

    /**
     * Create sample resources
     */
    private void createSampleResources() {
        log.info("Creating sample resources...");
        
        ResourceType serverType = resourceTypeRepository.findAll().stream()
                .filter(t -> t.getName().equals("Server"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Server type not found"));
        
        ResourceType gpuType = resourceTypeRepository.findAll().stream()
                .filter(t -> t.getName().equals("GPU"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("GPU type not found"));
        
        ResourceType switchType = resourceTypeRepository.findAll().stream()
                .filter(t -> t.getName().equals("Switch P4"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Switch P4 type not found"));
        
        // Create servers
        Resource server1 = new Resource();
        server1.setName("Server 1");
        server1.setSpecs("16GB RAM, 4 CPUs");
        server1.setLocation("DC1");
        server1.setStatus(ResourceStatus.ACTIVE);
        server1.setType(serverType);
        resourceRepository.save(server1);
        
        Resource server2 = new Resource();
        server2.setName("Server 2");
        server2.setSpecs("32GB RAM, 8 CPUs");
        server2.setLocation("DC1");
        server2.setStatus(ResourceStatus.ACTIVE);
        server2.setType(serverType);
        resourceRepository.save(server2);
        
        // Create GPU
        Resource gpu = new Resource();
        gpu.setName("NVIDIA Tesla");
        gpu.setSpecs("16GB VRAM");
        gpu.setLocation("DC2");
        gpu.setStatus(ResourceStatus.ACTIVE);
        gpu.setType(gpuType);
        resourceRepository.save(gpu);
        
        // Create Switch
        Resource switch1 = new Resource();
        switch1.setName("Switch P4 Alpha");
        switch1.setSpecs("100Gbps");
        switch1.setLocation("DC1");
        switch1.setStatus(ResourceStatus.MAINTENANCE);
        switch1.setType(switchType);
        resourceRepository.save(switch1);
        
        log.info("Sample resources created.");
    }

    /**
     * Create sample admin user
     */
    private void createSampleUser() {
        log.info("Creating sample admin user...");
        
        User adminUser = new User();
        adminUser.setName("Admin User");
        adminUser.setEmail("admin@example.com");
        adminUser.setKeycloakId("admin-keycloak-id");
        adminUser.setAvatar("AU");
        adminUser.setRoles(Set.of("ADMIN"));
        userRepository.save(adminUser);
        
        log.info("Sample admin user created.");
    }
}
