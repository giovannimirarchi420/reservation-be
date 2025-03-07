package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.Notification;
import it.polito.cloudresources.be.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Notification entity operations
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    /**
     * Find notifications by user
     */
    List<Notification> findByUser(User user);

    /**
     * Find notifications by user ID and read status, ordered by creation date
     */
    List<Notification> findByUserIdAndReadOrderByCreatedAtDesc(Long userId, boolean read);

    /**
     * Find all notifications for a user, ordered by creation date
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
}
