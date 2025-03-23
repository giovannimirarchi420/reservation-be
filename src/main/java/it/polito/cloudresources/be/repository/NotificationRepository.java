package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Notification entity operations
 * Now using Keycloak IDs instead of User entities
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    /**
     * Find notifications by Keycloak user ID and read status, ordered by creation date
     */
    List<Notification> findByKeycloakIdAndReadOrderByCreatedAtDesc(String keycloakId, boolean read);

    /**
     * Find all notifications for a user by Keycloak ID, ordered by creation date
     */
    List<Notification> findByKeycloakIdOrderByCreatedAtDesc(String keycloakId);
    
    /**
     * Count unread notifications for a user by Keycloak ID
     */
    int countByKeycloakIdAndRead(String keycloakId, boolean read);
}