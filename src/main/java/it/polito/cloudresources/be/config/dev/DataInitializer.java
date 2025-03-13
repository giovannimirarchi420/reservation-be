package it.polito.cloudresources.be.config.dev;

import it.polito.cloudresources.be.config.datetime.DateTimeConfig;
import it.polito.cloudresources.be.model.*;
import it.polito.cloudresources.be.repository.EventRepository;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import it.polito.cloudresources.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.ZonedDateTime;
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
    private final EventRepository eventRepository;

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
                createSampleUsers();
            }
            
            // Create sample events if none exist
            if (eventRepository.count() == 0) {
                createSampleEvents();
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
     * Create sample users
     */
    private void createSampleUsers() {
        log.info("Creating sample users...");
        
        User adminUser = new User();
        adminUser.setUsername("admin1");
        adminUser.setFirstName("Mario");
        adminUser.setLastName("Rossi");
        adminUser.setEmail("admin@example.com");
        adminUser.setAvatar("AU");
        adminUser.setRoles(Set.of("ADMIN"));
        userRepository.save(adminUser);
        
        User regularUser = new User();
        adminUser.setUsername("user1");
        adminUser.setFirstName("Dario");
        adminUser.setLastName("Argento");
        regularUser.setEmail("user@example.com");
        regularUser.setAvatar("RU");
        regularUser.setRoles(Set.of("USER"));
        userRepository.save(regularUser);
        
        log.info("Sample users created.");
    }
    
    /**
     * Create sample events
     */
    private void createSampleEvents() {
        log.info("Creating sample events...");
        
        // Get sample users
        User adminUser = userRepository.findByEmail("admin@example.com")
                .orElseThrow(() -> new RuntimeException("Admin user not found"));
        User regularUser = userRepository.findByEmail("user@example.com")
                .orElseThrow(() -> new RuntimeException("Regular user not found"));
                
        // Get sample resources
        Resource server1 = resourceRepository.findAll().stream()
                .filter(r -> r.getName().equals("Server 1"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Server 1 not found"));
                
        Resource gpu = resourceRepository.findAll().stream()
                .filter(r -> r.getName().equals("NVIDIA Tesla"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("GPU not found"));
        
        // Create events
        ZonedDateTime now = ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);
        
        Event event1 = new Event();
        event1.setTitle("Development work");
        event1.setDescription("Working on Project Alpha");
        event1.setStart(now.plusDays(1).withHour(9).withMinute(0).withSecond(0));
        event1.setEnd(now.plusDays(1).withHour(17).withMinute(0).withSecond(0));
        event1.setResource(server1);
        event1.setUser(adminUser);
        eventRepository.save(event1);
        
        Event event2 = new Event();
        event2.setTitle("ML Training");
        event2.setDescription("Training a new model");
        event2.setStart(now.plusDays(2).withHour(10).withMinute(0).withSecond(0));
        event2.setEnd(now.plusDays(2).withHour(16).withMinute(0).withSecond(0));
        event2.setResource(gpu);
        event2.setUser(regularUser);
        eventRepository.save(event2);
        
        // Past event
        Event event3 = new Event();
        event3.setTitle("Previous Booking");
        event3.setDescription("Completed work");
        event3.setStart(now.minusDays(3).withHour(9).withMinute(0).withSecond(0));
        event3.setEnd(now.minusDays(3).withHour(12).withMinute(0).withSecond(0));
        event3.setResource(server1);
        event3.setUser(regularUser);
        eventRepository.save(event3);
        
        log.info("Sample events created.");
    }
}