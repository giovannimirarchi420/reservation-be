package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Resource;
import it.polito.cloudresources.be.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Event entity operations
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    /**
     * Find events by user
     */
    List<Event> findByUser(User user);

    /**
     * Find events by resource
     */
    List<Event> findByResource(Resource resource);

    /**
     * Find events by resource ID
     */
    List<Event> findByResourceId(Long resourceId);

    /**
     * Find events by user ID
     */
    List<Event> findByUserId(Long userId);

    /**
     * Find events within a date range
     */
    @Query("SELECT e FROM Event e WHERE e.start >= :startDate AND e.end <= :endDate")
    List<Event> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find conflicting events for a resource in a time period
     */
    @Query("SELECT e FROM Event e WHERE e.resource.id = :resourceId " +
            "AND ((e.start <= :end AND e.end >= :start) OR " +
            "(e.start >= :start AND e.start <= :end) OR " +
            "(e.end >= :start AND e.end <= :end)) " +
            "AND (e.id != :eventId OR :eventId IS NULL)")
    List<Event> findConflictingEvents(
            @Param("resourceId") Long resourceId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("eventId") Long eventId);
}
