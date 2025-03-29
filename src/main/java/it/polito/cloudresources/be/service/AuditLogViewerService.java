package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.AuditLogDTO;
import it.polito.cloudresources.be.mapper.AuditLogMapper;
import it.polito.cloudresources.be.model.AuditLog;
import it.polito.cloudresources.be.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for retrieving and searching audit logs
 */
@Service
@RequiredArgsConstructor
public class AuditLogViewerService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    /**
     * Get all audit logs with pagination
     */
    public List<AuditLogDTO> getAllLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        return auditLogRepository.findAll(pageable)
                .stream()
                .map(auditLogMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get a single audit log by ID
     */
    public Optional<AuditLogDTO> getLogById(Long id) {
        return auditLogRepository.findById(id)
                .map(auditLogMapper::toDto);
    }

    /**
     * Get logs by user ID
     */
    public List<AuditLogDTO> getLogsByUsername(String userId) {
        return auditLogRepository.findByUsernameOrderByTimestampDesc(userId)
                .stream()
                .map(auditLogMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get logs by entity type and ID
     */
    public List<AuditLogDTO> getLogsByEntity(String entityType, String entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId)
                .stream()
                .map(auditLogMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get logs by log type
     */
    public List<AuditLogDTO> getLogsByType(AuditLog.LogType logType) {
        return auditLogRepository.findByLogTypeOrderByTimestampDesc(logType)
                .stream()
                .map(auditLogMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get logs by action
     */
    public List<AuditLogDTO> getLogsByAction(AuditLog.LogAction action) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action)
                .stream()
                .map(auditLogMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get logs by severity
     */
    public List<AuditLogDTO> getLogsBySeverity(AuditLog.LogSeverity severity) {
        return auditLogRepository.findBySeverityOrderByTimestampDesc(severity)
                .stream()
                .map(auditLogMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get logs by date range
     */
    public List<AuditLogDTO> getLogsByDateRange(ZonedDateTime startDate, ZonedDateTime endDate) {
        return auditLogRepository.findByDateRange(startDate, endDate)
                .stream()
                .map(auditLogMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Search logs by details text
     */
    public List<AuditLogDTO> searchLogs(String searchText) {
        return auditLogRepository.findByDetailsContainingIgnoreCaseOrderByTimestampDesc(searchText)
                .stream()
                .map(auditLogMapper::toDto)
                .collect(Collectors.toList());
    }
}