package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.Event;
import it.polito.cloudresources.be.model.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Repository for Event entity operations
 * Now using Keycloak IDs instead of User entities
 */
@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    /**
     * Find events by user's Keycloak ID
     */
    List<Event> findByKeycloakId(String keycloakId);

    /**
     * Find events by resource
     */
    List<Event> findByResource(Resource resource);

    /**
     * Find events by multiple resource IDs (for site-based filtering)
     */
    List<Event> findByResourceIdIn(List<Long> resourceIds);

    /**
     * Find events by resource ID
     */
    List<Event> findByResourceId(Long resourceId);
    /**
     * Find events within a date range
     */
    @Query("SELECT e FROM Event e WHERE e.start >= :startDate AND e.end <= :endDate")
    List<Event> findByDateRange(
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate);

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
            @Param("start") ZonedDateTime start,
            @Param("end") ZonedDateTime end,
            @Param("eventId") Long eventId);

    /**
     *
     * @param siteIds
     * @return All events related to the input sites
     */
    @Query("SELECT e FROM Event e JOIN e.resource r WHERE r.siteId IN :siteIds")
    List<Event> findBySiteIds(@Param("siteIds") List<String> siteIds);

    /**
     *
     * @param siteId
     * @return All events related to the input site
     */
    @Query("SELECT e FROM Event e JOIN e.resource r WHERE r.siteId = :siteId")
    List<Event> findBySiteId(@Param("siteId") String siteId);
}