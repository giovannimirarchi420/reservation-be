package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.WebhookLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Repository for WebhookLog entity operations
 */
@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long> {
    
    /**
     * Find logs by webhook ID
     */
    List<WebhookLog> findByWebhookId(Long webhookId);
    
    /**
     * Find logs by webhook ID with pagination
     */
    Page<WebhookLog> findByWebhookId(Long webhookId, Pageable pageable);
    
    /**
     * Find logs by webhook ID and success status
     */
    Page<WebhookLog> findByWebhookIdAndSuccess(Long webhookId, boolean success, Pageable pageable);
    
    /**
     * Find logs by webhook IDs
     */
    List<WebhookLog> findByWebhookIdIn(List<Long> webhookIds);
    
    /**
     * Find logs by webhook IDs and success status
     */
    List<WebhookLog> findByWebhookIdInAndSuccess(List<Long> webhookIds, boolean success);
    
    /**
     * Find logs by success status
     */
    List<WebhookLog> findBySuccess(boolean success);
    
    /**
     * Find failed logs that are due for retry
     */
    @Query("SELECT l FROM WebhookLog l WHERE l.success = false AND " +
           "l.nextRetryAt <= :now AND l.retryCount < l.webhook.maxRetries")
    List<WebhookLog> findPendingRetries(@Param("now") ZonedDateTime now);
    
    /**
     * Find logs for a specific resource
     */
    List<WebhookLog> findByResourceId(Long resourceId);
}