package it.polito.cloudresources.be.config.dev;

import it.polito.cloudresources.be.config.datetime.DateTimeConfig;
import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Notification;
import it.polito.cloudresources.be.model.NotificationType;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.ResourceStatus;
import it.polito.cloudresources.be.model.ResourceType;
import it.polito.cloudresources.be.model.WebhookConfig;
import it.polito.cloudresources.be.model.WebhookEventType;
import it.polito.cloudresources.be.repository.EventRepository;
import it.polito.cloudresources.be.repository.NotificationRepository;
import it.polito.cloudresources.be.repository.ResourceRepository;
import it.polito.cloudresources.be.repository.ResourceTypeRepository;
import it.polito.cloudresources.be.repository.WebhookConfigRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Configuration for initializing sample resources and events for development
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
    private final NotificationRepository notificationRepository;
    private final WebhookConfigRepository webhookConfigRepository;
    private final KeycloakService keycloakService;

        /**
     * Initialize sample data
     */
    @Bean
    public CommandLineRunner initData() {
        return arg -> {
            try {
                log.info("Initializing sample data...");

                // Clear existing data to prevent duplicate keys
                clearExistingData();

                // Get site IDs
                Map<String, String> siteMap = getSiteIdMap();
                if (siteMap.isEmpty()) {
                    log.warn("No sites found. Please run SiteDataInitializer first.");
                    return;
                }

                // Create resource types if none exist
                if (resourceTypeRepository.count() == 0) {
                    createResourceTypes(siteMap);
                }

                // Create sample resources if none exist
                if (resourceRepository.count() == 0) {
                    createSampleResources();
                }

                // Create sample events if none exist
                if (eventRepository.count() == 0) {
                    createSampleEvents();
                }
                
                // Create sample webhooks if none exist
                if (webhookConfigRepository.count() == 0) {
                    createSampleWebhooks();
                }
                
                // Create sample notifications
                createSampleNotifications();

                logDataSummary();
                log.info("Sample data initialization complete.");
            } catch (Exception e) {
                log.error("Error initializing sample data: {}", e.getMessage(), e);
                throw e;
            }
        };
    }

    /**
     * Clear existing data to prevent conflicts
     */
    private void clearExistingData() {
        try {
            log.info("Clearing existing data...");
            
            // Clear data in correct order to avoid constraint violations
            webhookConfigRepository.deleteAll();
            notificationRepository.deleteAll();
            eventRepository.deleteAll();
            resourceRepository.deleteAll();
            resourceTypeRepository.deleteAll();
            
            log.info("Existing data cleared successfully.");
        } catch (Exception e) {
            log.error("Error clearing existing data: {}", e.getMessage());
            // Continue anyway
        }
    }

    /**
     * Log summary of created data
     */
    private void logDataSummary() {
        log.info("======= DATA INITIALIZATION SUMMARY =======");
        log.info("Resource Types: {}", resourceTypeRepository.count());
        log.info("Resources: {}", resourceRepository.count());
        log.info("Events: {}", eventRepository.count());
        log.info("Webhooks: {}", webhookConfigRepository.count());
        log.info("Notifications: {}", notificationRepository.count());
        log.info("==========================================");
    }
    /**
     * Get site IDs by name for easy reference
     */
    private Map<String, String> getSiteIdMap() {
        try {
            List<GroupRepresentation> sites = keycloakService.getAllGroups();
            return sites.stream()
                    .collect(Collectors.toMap(
                            GroupRepresentation::getName,
                            GroupRepresentation::getId,
                            // In case of duplicate names, keep the first one
                            (id1, id2) -> id1
                    ));
        } catch (Exception e) {
            log.error("Error fetching sites: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    /**
     * Create resource types
     */
    private void createResourceTypes(Map<String, String> siteMap) {
        log.info("Creating resource types...");

        if (siteMap.isEmpty()) {
            log.warn("No sites found. Please run SiteDataInitializer first.");
            return;
        }

        String poliToSiteId = siteMap.get("polito");
        String uniRomaSiteId = siteMap.get("uniroma");
        String uniMiSiteId = siteMap.get("unimi");

        // Fallback if specific sites are not found
        String defaultSiteId = siteMap.values().iterator().next();
        
        if (poliToSiteId == null) {
            log.warn("PoliTo site not found, using default site");
            poliToSiteId = defaultSiteId;
        }
        
        if (uniRomaSiteId == null) {
            log.warn("UniRoma site not found, using default site");
            uniRomaSiteId = defaultSiteId;
        }
        
        if (uniMiSiteId == null) {
            log.warn("UniMi site not found, using default site");
            uniMiSiteId = defaultSiteId;
        }

        ResourceType serverType = new ResourceType();
        serverType.setName("Server");
        serverType.setColor("#1976d2");
        serverType.setSiteId(poliToSiteId);
        resourceTypeRepository.save(serverType);

        ResourceType gpuType = new ResourceType();
        gpuType.setName("GPU");
        gpuType.setColor("#4caf50");
        gpuType.setSiteId(uniRomaSiteId);
        resourceTypeRepository.save(gpuType);

        ResourceType switchType = new ResourceType();
        switchType.setName("Switch");
        switchType.setColor("#ff9800");
        switchType.setSiteId(uniMiSiteId);
        resourceTypeRepository.save(switchType);
        
        ResourceType storageType = new ResourceType();
        storageType.setName("Storage");
        storageType.setColor("#e91e63");
        storageType.setSiteId(poliToSiteId);
        resourceTypeRepository.save(storageType);

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

        ResourceType serverType = findResourceTypeByName(types, "Server");
        ResourceType gpuType = findResourceTypeByName(types, "GPU");
        ResourceType switchType = findResourceTypeByName(types, "Switch");
        ResourceType storageType = findResourceTypeByName(types, "Storage");

        // Create parent resources
        Resource datacenter1 = new Resource();
        datacenter1.setName("Datacenter Turin");
        datacenter1.setSpecs("Main datacenter facility");
        datacenter1.setLocation("Turin, Italy");
        datacenter1.setStatus(ResourceStatus.ACTIVE);
        datacenter1.setType(serverType);
        datacenter1.setSiteId(serverType.getSiteId());
        datacenter1.setParent(null);
        resourceRepository.save(datacenter1);

        Resource datacenter2 = new Resource();
        datacenter2.setName("Datacenter Rome");
        datacenter2.setSpecs("Secondary datacenter facility");
        datacenter2.setLocation("Rome, Italy");
        datacenter2.setStatus(ResourceStatus.ACTIVE);
        datacenter2.setType(gpuType);
        datacenter2.setSiteId(gpuType.getSiteId());
        datacenter2.setParent(null);
        resourceRepository.save(datacenter2);

        // Create servers
        Resource server1 = new Resource();
        server1.setName("Server Alpha");
        server1.setSpecs("16GB RAM, 4 CPUs, 1TB SSD");
        server1.setLocation("Rack A1");
        server1.setStatus(ResourceStatus.ACTIVE);
        server1.setType(serverType);
        server1.setSiteId(serverType.getSiteId());
        server1.setParent(datacenter1);
        resourceRepository.save(server1);

        Resource server2 = new Resource();
        server2.setName("Server Beta");
        server2.setSpecs("32GB RAM, 8 CPUs, 2TB SSD");
        server2.setLocation("Rack A2");
        server2.setStatus(ResourceStatus.ACTIVE);
        server2.setType(serverType);
        server2.setSiteId(serverType.getSiteId());
        server2.setParent(datacenter1);
        resourceRepository.save(server2);

        Resource server3 = new Resource();
        server3.setName("Server Gamma");
        server3.setSpecs("64GB RAM, 16 CPUs, 4TB SSD");
        server3.setLocation("Rack B1");
        server3.setStatus(ResourceStatus.MAINTENANCE);
        server3.setType(serverType);
        server3.setSiteId(serverType.getSiteId());
        server3.setParent(datacenter1);
        resourceRepository.save(server3);

        // Create GPU resources
        Resource gpu1 = new Resource();
        gpu1.setName("NVIDIA Tesla A100");
        gpu1.setSpecs("40GB VRAM, 6912 CUDA Cores");
        gpu1.setLocation("Rack C1");
        gpu1.setStatus(ResourceStatus.ACTIVE);
        gpu1.setType(gpuType);
        gpu1.setSiteId(gpuType.getSiteId());
        gpu1.setParent(datacenter2);
        resourceRepository.save(gpu1);

        Resource gpu2 = new Resource();
        gpu2.setName("NVIDIA Tesla V100");
        gpu2.setSpecs("32GB VRAM, 5120 CUDA Cores");
        gpu2.setLocation("Rack C2");
        gpu2.setStatus(ResourceStatus.ACTIVE);
        gpu2.setType(gpuType);
        gpu2.setSiteId(gpuType.getSiteId());
        gpu2.setParent(datacenter2);
        resourceRepository.save(gpu2);

        // Create switches
        Resource switch1 = new Resource();
        switch1.setName("Cisco Nexus 9000");
        switch1.setSpecs("100Gbps, 48 ports");
        switch1.setLocation("Rack D1");
        switch1.setStatus(ResourceStatus.ACTIVE);
        switch1.setType(switchType);
        switch1.setSiteId(switchType.getSiteId());
        resourceRepository.save(switch1);

        Resource switch2 = new Resource();
        switch2.setName("Juniper EX4650");
        switch2.setSpecs("100Gbps, 48 ports");
        switch2.setLocation("Rack D2");
        switch2.setStatus(ResourceStatus.UNAVAILABLE);
        switch2.setType(switchType);
        switch2.setSiteId(switchType.getSiteId());
        resourceRepository.save(switch2);
        
        // Create storage resources
        Resource storage1 = new Resource();
        storage1.setName("NetApp FAS8700");
        storage1.setSpecs("100TB, SSD-based");
        storage1.setLocation("Rack E1");
        storage1.setStatus(ResourceStatus.ACTIVE);
        storage1.setType(storageType);
        storage1.setSiteId(storageType.getSiteId());
        storage1.setParent(datacenter1);
        resourceRepository.save(storage1);

        log.info("Sample resources created.");
    }

    /**
     * Find resource type by name
     */
    private ResourceType findResourceTypeByName(List<ResourceType> types, String name) {
        return types.stream()
                .filter(t -> t.getName().equals(name))
                .findFirst()
                .orElse(types.get(0));
    }

    /**
     * Create sample events
     */
    private void createSampleEvents() {
        log.info("Creating sample events...");

        // Get user IDs
        String adminId = getUserIdByUsername("admin1");
        String poliToAdminId = getUserIdByUsername("polito_admin");
        String uniRomaAdminId = getUserIdByUsername("uniroma_admin");
        String userPoliToId = getUserIdByUsername("user_polito");
        String userUniRomaId = getUserIdByUsername("user_uniroma");

        if (adminId == null || poliToAdminId == null || userPoliToId == null) {
            log.warn("Required users not found. Events cannot be created.");
            return;
        }

        // Get resources
        List<Resource> resources = resourceRepository.findAll();
        if (resources.isEmpty()) {
            log.warn("No resources found. Events cannot be created.");
            return;
        }

        // Create events for different time periods
        ZonedDateTime now = ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);
        
        // Past events
        createEvent("Past Booking 1", "Completed work", resources.get(0), 
                adminId, now.minusDays(10).withHour(9), now.minusDays(10).withHour(17));
        
        createEvent("Past Booking 2", "Testing completed", resources.get(1), 
                poliToAdminId, now.minusDays(5).withHour(10), now.minusDays(5).withHour(15));
        
        createEvent("Past Booking 3", "Development session", resources.get(2), 
                userPoliToId, now.minusDays(3).withHour(13), now.minusDays(3).withHour(16));
        
        // Current events (today)
        createEvent("Current Booking", "Ongoing work", resources.get(0), 
                adminId, now.withHour(8), now.withHour(18));
        
        // Future events
        createEvent("Future Booking 1", "Planned maintenance", resources.get(1), 
                poliToAdminId, now.plusDays(1).withHour(9), now.plusDays(1).withHour(17));
        
        createEvent("Future Booking 2", "Development sprint", resources.get(2), 
                userPoliToId, now.plusDays(2).withHour(10), now.plusDays(2).withHour(16));
        
        createEvent("Future Booking 3", "Testing session", resources.get(3), 
                adminId, now.plusDays(3).withHour(9), now.plusDays(3).withHour(12));
        
        if (userUniRomaId != null && resources.size() > 4) {
            createEvent("Future Booking 4", "Research work", resources.get(4), 
                    userUniRomaId, now.plusDays(4).withHour(13), now.plusDays(4).withHour(18));
        }
        
        if (uniRomaAdminId != null && resources.size() > 5) {
            // Long-term booking
            createEvent("Long-term Project", "Infrastructure upgrade", resources.get(5), 
                    uniRomaAdminId, now.plusDays(7).withHour(9), now.plusDays(14).withHour(17));
        }

        log.info("Sample events creation completed.");
    }
    
    /**
     * Helper method to create an event
     */
    private void createEvent(String title, String description, Resource resource, 
                           String userId, ZonedDateTime start, ZonedDateTime end) {
        try {
            Event event = new Event();
            event.setTitle(title);
            event.setDescription(description);
            event.setStart(start);
            event.setEnd(end);
            event.setResource(resource);
            event.setKeycloakId(userId);
            eventRepository.save(event);
            log.debug("Created event: {} for user {} on resource {}", 
                     title, userId, resource.getName());
        } catch (Exception e) {
            log.error("Error creating event {}: {}", title, e.getMessage());
        }
    }
    
    /**
     * Create sample webhooks
     */
    private void createSampleWebhooks() {
        log.info("Creating sample webhooks...");
        
        List<Resource> resources = resourceRepository.findAll();
        List<ResourceType> resourceTypes = resourceTypeRepository.findAll();
        
        if (resources.isEmpty() || resourceTypes.isEmpty()) {
            log.warn("No resources or resource types found. Webhooks cannot be created.");
            return;
        }
        
        // Webhook for specific resource
        WebhookConfig webhook1 = new WebhookConfig();
        webhook1.setName("Resource Status Monitor");
        webhook1.setUrl("https://webhook.site/demo-hook-1");
        webhook1.setEventType(WebhookEventType.RESOURCE_STATUS_CHANGED);
        webhook1.setEnabled(true);
        webhook1.setResource(resources.get(0));
        webhook1.setResourceType(null);
        webhook1.setSiteId(resources.get(0).getSiteId());
        webhook1.setMaxRetries(3);
        webhook1.setRetryDelaySeconds(60);
        webhook1.setSecret("sample-secret-key-1");
        webhookConfigRepository.save(webhook1);
        
        // Webhook for resource type
        WebhookConfig webhook2 = new WebhookConfig();
        webhook2.setName("Server Events");
        webhook2.setUrl("https://webhook.site/demo-hook-2");
        webhook2.setEventType(WebhookEventType.ALL);
        webhook2.setEnabled(true);
        webhook2.setResource(null);
        webhook2.setResourceType(resourceTypes.get(0));
        webhook2.setSiteId(resourceTypes.get(0).getSiteId());
        webhook2.setMaxRetries(3);
        webhook2.setRetryDelaySeconds(60);
        webhook2.setSecret("sample-secret-key-2");
        webhookConfigRepository.save(webhook2);
        
        // Webhook for booking events
        WebhookConfig webhook3 = new WebhookConfig();
        webhook3.setName("Booking Tracker");
        webhook3.setUrl("https://webhook.site/demo-hook-3");
        webhook3.setEventType(WebhookEventType.EVENT_CREATED);
        webhook3.setEnabled(true);
        webhook3.setResource(null);
        webhook3.setResourceType(null);
        webhook3.setSiteId(resources.get(1).getSiteId());
        webhook3.setMaxRetries(3);
        webhook3.setRetryDelaySeconds(60);
        webhook3.setSecret("sample-secret-key-3");
        webhookConfigRepository.save(webhook3);
        
        log.info("Sample webhooks created.");
    }
    
    /**
     * Create sample notifications
     */
    private void createSampleNotifications() {
        log.info("Creating sample notifications...");
        
        String adminId = getUserIdByUsername("admin1");
        String poliToAdminId = getUserIdByUsername("polito_admin");
        String userPoliToId = getUserIdByUsername("user_polito");
        
        if (adminId == null || poliToAdminId == null || userPoliToId == null) {
            log.warn("Required users not found. Notifications cannot be created.");
            return;
        }
        
        // Create notifications for different users directly through repository
        createNotification(adminId, "Welcome to Cloud Resource Management System", NotificationType.INFO);
        createNotification(adminId, "New resources available for booking", NotificationType.SUCCESS);
        createNotification(poliToAdminId, "Server maintenance scheduled for next week", NotificationType.WARNING);
        createNotification(userPoliToId, "Your booking request has been approved", NotificationType.SUCCESS);
        createNotification(userPoliToId, "Don't forget to check resource status before booking", NotificationType.INFO);
        
        log.info("Sample notifications created.");
    }
    
    /**
     * Helper method to create a notification directly
     */
    private void createNotification(String keycloakId, String message, NotificationType type) {
        try {
            Notification notification = new Notification();
            notification.setKeycloakId(keycloakId);
            notification.setMessage(message);
            notification.setType(type);
            notification.setRead(false);
            
            notificationRepository.save(notification);
            log.debug("Created notification for user {}: {}", keycloakId, message);
        } catch (Exception e) {
            log.error("Error creating notification: {}", e.getMessage());
        }
    }

    /**
     * Get user ID by username
     */
    private String getUserIdByUsername(String username) {
        try {
            Optional<UserRepresentation> user = keycloakService.getUserByUsername(username);
            return user.map(UserRepresentation::getId).orElse(null);
        } catch (Exception e) {
            log.error("Error getting user ID for username {}: {}", username, e.getMessage());
            return null;
        }
    }
}