package it.polito.cloudresources.be.repository;

import it.polito.cloudresources.be.model.AuditLog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for AuditLog entity operations
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Search logs by details containing a text
     */
    Page<AuditLog> findByDetailsContainingIgnoreCase(String searchText, Pageable pageable);

}