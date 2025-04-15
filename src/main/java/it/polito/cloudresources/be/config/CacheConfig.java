package it.polito.cloudresources.be.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;

import it.polito.cloudresources.be.service.KeycloakService;

/**
 * Configuration for caching in the application
 * Specifically designed to improve Keycloak service performance
 */
@Configuration
@EnableCaching
@Profile("!dev") // Only use in non-dev environments where we use the real Keycloak service
public class CacheConfig {
    
    /**
     * Configure the cache manager with all required caches
     */
    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        
        // Configure all caches needed by KeycloakService
        cacheManager.setCacheNames(Arrays.asList(
            KeycloakService.USERS_CACHE,
            KeycloakService.USER_BY_ID_CACHE,
            KeycloakService.USER_BY_USERNAME_CACHE,
            KeycloakService.USER_BY_EMAIL_CACHE,
            KeycloakService.USER_ROLES_CACHE,
            KeycloakService.USER_ATTRIBUTES_CACHE,
            KeycloakService.USER_GROUPS_CACHE,
            KeycloakService.GROUPS_CACHE,
            KeycloakService.GROUP_BY_ID_CACHE,
            KeycloakService.GROUP_BY_NAME_CACHE,
            KeycloakService.GROUP_MEMBERS_CACHE,
            KeycloakService.USERS_IN_GROUP_CACHE,
            KeycloakService.USER_ADMIN_GROUPS_CACHE,
            KeycloakService.USER_SITES_CACHE,
            KeycloakService.USER_BY_ROLE_CACHE,
            KeycloakService.USER_SITE_ADMIN_STATUS,
            "keycloak_user_global_admin",
            "keycloak_user_admin_group_ids"
        ));
        
        return cacheManager;
    }
}