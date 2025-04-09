package it.polito.cloudresources.be.config.dev;

import it.polito.cloudresources.be.config.datetime.DateTimeConfig;
import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.repository.EventRepository;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import it.polito.cloudresources.be.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Configuration for initializing sample resources and events for development
 * Updated to work with the site-specific admin roles approach
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("dev") // Only run in development profile
@DependsOn("initSites") // Make sure sites are initialized first
public class DataInitializer {

    private final ResourceTypeRepository resourceTypeRepository;
    private final ResourceRepository resourceRepository;
    private final EventRepository eventRepository;
    private final KeycloakService keycloakService;

    /**
     * Initialize sample data
     */
    @Bean
    public CommandLineRunner initData() {
        return arg -> {
            log.info("Initializing sample data...");

            // Create resource types if none exist
            if (resourceTypeRepository.count() == 0) {
                createResourceTypes();
            }

            // Create sample resources if none exist
            if (resourceRepository.count() == 0) {
                createSampleResources();
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

        List<String> siteIds = keycloakService.getAllGroups().stream()
                .map(GroupRepresentation::getId)
                .collect(Collectors.toList());

        if (siteIds.isEmpty()) {
            log.warn("No sites found. Please run SiteDataInitializer first.");
            return;
        }

        String poliToSiteId = siteIds.get(0);
        String secondSiteId = siteIds.size() > 1 ? siteIds.get(1) : poliToSiteId;
        String thirdSiteId = siteIds.size() > 2 ? siteIds.get(2) : poliToSiteId;

        ResourceType serverType = new ResourceType();
        serverType.setName("Server");
        serverType.setColor("#1976d2");
        serverType.setSiteId(poliToSiteId);
        resourceTypeRepository.save(serverType);

        ResourceType gpuType = new ResourceType();
        gpuType.setName("GPU");
        gpuType.setColor("#4caf50");
        gpuType.setSiteId(secondSiteId);
        resourceTypeRepository.save(gpuType);

        ResourceType switchType = new ResourceType();
        switchType.setName("Switch P4");
        switchType.setColor("#ff9800");
        switchType.setSiteId(thirdSiteId);
        resourceTypeRepository.save(switchType);

        log.info("Resource types created.");
    }

    /**
     * Create sample resources
     */
    private void createSampleResources() {
        log.info("Creating sample resources...");

        List<ResourceType> types = resourceTypeRepository.findAll();
        if (types.isEmpty()) {
            log.warn("No resource types found. Resources cannot be created.");
            return;
        }

        ResourceType serverType = types.stream()
                .filter(t -> t.getName().equals("Server"))
                .findFirst()
                .orElse(types.get(0));

        ResourceType gpuType = types.stream()
                .filter(t -> t.getName().equals("GPU"))
                .findFirst()
                .orElse(types.get(types.size() > 1 ? 1 : 0));

        ResourceType switchType = types.stream()
                .filter(t -> t.getName().equals("Switch P4"))
                .findFirst()
                .orElse(types.get(types.size() > 2 ? 2 : 0));

        // Create servers
        Resource server1 = new Resource();
        server1.setName("Server 1");
        server1.setSpecs("16GB RAM, 4 CPUs");
        server1.setLocation("DC1");
        server1.setStatus(ResourceStatus.ACTIVE);
        server1.setType(serverType);
        server1.setSiteId(serverType.getSiteId());
        resourceRepository.save(server1);

        Resource server2 = new Resource();
        server2.setName("Server 2");
        server2.setSpecs("32GB RAM, 8 CPUs");
        server2.setLocation("DC1");
        server2.setStatus(ResourceStatus.ACTIVE);
        server2.setType(serverType);
        server2.setSiteId(serverType.getSiteId());
        resourceRepository.save(server2);

        // Create GPU
        Resource gpu = new Resource();
        gpu.setName("NVIDIA Tesla");
        gpu.setSpecs("16GB VRAM");
        gpu.setLocation("DC2");
        gpu.setStatus(ResourceStatus.ACTIVE);
        gpu.setType(gpuType);
        gpu.setSiteId(gpuType.getSiteId());
        resourceRepository.save(gpu);

        // Create Switch
        Resource switch1 = new Resource();
        switch1.setName("Switch P4 Alpha");
        switch1.setSpecs("100Gbps");
        switch1.setLocation("DC1");
        switch1.setStatus(ResourceStatus.MAINTENANCE);
        switch1.setType(switchType);
        switch1.setSiteId(switchType.getSiteId());
        resourceRepository.save(switch1);

        log.info("Sample resources created.");
    }

    /**
     * Create sample events
     */
    private void createSampleEvents() {
        log.info("Creating sample events...");

        // Get sample user IDs
        String adminKeycloakId = keycloakService.getUserByUsername("admin1")
                .map(UserRepresentation::getId)
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        String regularUserKeycloakId = keycloakService.getUserByUsername("user1")
                .map(UserRepresentation::getId)
                .orElseThrow(() -> new RuntimeException("Regular user not found"));

        // Get sample resources
        List<Resource> resources = resourceRepository.findAll();
        if (resources.isEmpty()) {
            log.warn("No resources found. Events cannot be created.");
            return;
        }

        Resource resource1 = resources.get(0);
        Resource resource2 = resources.size() > 1 ? resources.get(1) : resource1;

        // Create events
        ZonedDateTime now = ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);

        // Check if users have access to these resources
        boolean adminHasAccess1 = keycloakService.isUserInGroup(adminKeycloakId, resource1.getSiteId());
        boolean userHasAccess2 = keycloakService.isUserInGroup(regularUserKeycloakId, resource2.getSiteId());

        // Create event for admin
        if (adminHasAccess1) {
            Event event1 = new Event();
            event1.setTitle("Development work");
            event1.setDescription("Working on Project Alpha");
            event1.setStart(now.plusDays(1).withHour(9).withMinute(0).withSecond(0));
            event1.setEnd(now.plusDays(1).withHour(17).withMinute(0).withSecond(0));
            event1.setResource(resource1);
            event1.setKeycloakId(adminKeycloakId);
            eventRepository.save(event1);
            log.info("Created event for admin on resource {}", resource1.getName());
        } else {
            log.warn("Admin does not have access to resource {}. Make sure user is in site {}.",
                    resource1.getName(), resource1.getSiteId());
        }

        // Create event for user
        if (userHasAccess2) {
            Event event2 = new Event();
            event2.setTitle("ML Training");
            event2.setDescription("Training a new model");
            event2.setStart(now.plusDays(2).withHour(10).withMinute(0).withSecond(0));
            event2.setEnd(now.plusDays(2).withHour(16).withMinute(0).withSecond(0));
            event2.setResource(resource2);
            event2.setKeycloakId(regularUserKeycloakId);
            eventRepository.save(event2);
            log.info("Created event for regular user on resource {}", resource2.getName());
        } else {
            // Try to find an accessible resource for the user
            Optional<Resource> accessibleResource = resources.stream()
                    .filter(r -> keycloakService.isUserInGroup(regularUserKeycloakId, r.getSiteId()))
                    .findFirst();

            if (accessibleResource.isPresent()) {
                Event event2 = new Event();
                event2.setTitle("ML Training");
                event2.setDescription("Training a new model");
                event2.setStart(now.plusDays(2).withHour(10).withMinute(0).withSecond(0));
                event2.setEnd(now.plusDays(2).withHour(16).withMinute(0).withSecond(0));
                event2.setResource(accessibleResource.get());
                event2.setKeycloakId(regularUserKeycloakId);
                eventRepository.save(event2);
                log.info("Created event for regular user on resource {}", accessibleResource.get().getName());
            } else {
                log.warn("Regular user does not have access to any resources. Make sure user is in at least one site.");
            }
        }

        // Past event for admin (if has access)
        if (adminHasAccess1) {
            Event event3 = new Event();
            event3.setTitle("Previous Booking");
            event3.setDescription("Completed work");
            event3.setStart(now.minusDays(3).withHour(9).withMinute(0).withSecond(0));
            event3.setEnd(now.minusDays(3).withHour(12).withMinute(0).withSecond(0));
            event3.setResource(resource1);
            event3.setKeycloakId(adminKeycloakId);
            eventRepository.save(event3);
            log.info("Created past event for admin");
        }

        log.info("Sample events creation completed.");
    }
}