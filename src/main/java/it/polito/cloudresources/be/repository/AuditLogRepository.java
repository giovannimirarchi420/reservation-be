package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.AuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Repository for AuditLog entity operations
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find logs by user ID
     */
    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);

    /**
     * Find logs by entity type and entity ID
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, String entityId);

    /**
     * Find logs by log type
     */
    List<AuditLog> findByLogTypeOrderByTimestampDesc(AuditLog.LogType logType);

    /**
     * Find logs by action
     */
    List<AuditLog> findByActionOrderByTimestampDesc(AuditLog.LogAction action);

    /**
     * Find logs by severity
     */
    List<AuditLog> findBySeverityOrderByTimestampDesc(AuditLog.LogSeverity severity);

    /**
     * Find logs by date range
     */
    @Query("SELECT a FROM AuditLog a WHERE a.timestamp BETWEEN :startDate AND :endDate ORDER BY a.timestamp DESC")
    List<AuditLog> findByDateRange(
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate);

    /**
     * Search logs by details containing a text
     */
    Page<AuditLog> findByDetailsContainingIgnoreCase(String searchText, Pageable pageable);}