package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.WebhookConfig;
import it.polito.cloudresources.be.model.WebhookEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for WebhookConfig entity operations
 */
@Repository
public interface WebhookConfigRepository extends JpaRepository<WebhookConfig, Long> {
    
    /**
     * Find webhooks by event type and enabled status
     */
    List<WebhookConfig> findByEventTypeAndEnabled(WebhookEventType eventType, boolean enabled);
    
    /**
     * Find webhooks for a specific resource
     */
    List<WebhookConfig> findByResourceIdAndEnabled(Long resourceId, boolean enabled);

    List<WebhookConfig> findByResourceId(Long resourceId);
    
    /**
     * Find webhooks for a specific resource type
     */
    List<WebhookConfig> findByResourceTypeIdAndEnabled(Long resourceTypeId, boolean enabled);
    
    /**
     * Find webhooks for resources in a site
     */
    @Query("SELECT w FROM WebhookConfig w JOIN w.resource r WHERE r.siteId = :siteId AND w.enabled = :enabled")
    List<WebhookConfig> findByResourceSiteIdAndEnabled(@Param("siteId") String siteId, @Param("enabled") boolean enabled);
    
    /**
     * Find webhooks for resource types in a site
     */
    @Query("SELECT w FROM WebhookConfig w JOIN w.resourceType rt WHERE rt.siteId = :siteId AND w.enabled = :enabled")
    List<WebhookConfig> findByResourceTypeSiteIdAndEnabled(@Param("siteId") String siteId, @Param("enabled") boolean enabled);
    
    /**
     * Find webhooks by IDs
     */
    List<WebhookConfig> findByIdIn(List<Long> ids);
    
    /**
     * Find relevant webhooks for a specific resource event
     * This includes:
     * - Webhooks for this specific resource
     * - Webhooks for any parent resource (with includeSubResources=true)
     * - Webhooks for the resource type
     */
    @Query("SELECT DISTINCT w FROM WebhookConfig w WHERE w.enabled = true AND " +
    "(" +
        "(w.resource.id = :resourceId) OR " + 
        "(w.resource.id IN (SELECT p.id FROM Resource r JOIN r.parent p WHERE r.id = :resourceId) AND w.includeSubResources = true) OR " +
        "(w.resourceType.id = (SELECT r.type.id FROM Resource r WHERE r.id = :resourceId)) OR " +
        "(w.siteId = (SELECT r.siteId FROM Resource r WHERE r.id = :resourceId) AND w.resource IS NULL AND w.resourceType IS NULL)" +
    ") AND " +
    "(w.eventType = :eventType OR w.eventType = 'ALL')")
    List<WebhookConfig> findRelevantWebhooksForResourceEvent(
       @Param("resourceId") Long resourceId, 
       @Param("eventType") WebhookEventType eventType);

    /**
     * Find webhooks by site IDs
     */
    List<WebhookConfig> findBySiteId(String siteId);
}