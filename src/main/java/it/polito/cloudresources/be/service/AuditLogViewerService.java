package it.polito.cloudresources.be.service;

import it.polito.cloudresources.be.dto.AuditLogDTO;
import it.polito.cloudresources.be.dto.logs.EnhancedAuditLogResponseDTO;
import it.polito.cloudresources.be.mapper.AuditLogMapper;
import it.polito.cloudresources.be.model.AuditLog;
import it.polito.cloudresources.be.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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
     * Search logs by details text
     */
    public List<AuditLogDTO> searchLogs(String searchText, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());

        return auditLogRepository.findByDetailsContainingIgnoreCase(searchText, pageable)
                .stream()
                .map(auditLogMapper::toDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Get log statistics
     */
    public EnhancedAuditLogResponseDTO getLogStatistics(List<AuditLogDTO> logs) {
        EnhancedAuditLogResponseDTO response = new EnhancedAuditLogResponseDTO(logs);
        
        // Get total count
        response.setTotalElements(auditLogRepository.count());
        
        // Get admin logs count
        response.setAdminLogsCount(auditLogRepository.countByLogType(AuditLog.LogType.ADMIN));
        
        // Get user logs count
        response.setUserLogsCount(auditLogRepository.countByLogType(AuditLog.LogType.USER));
        
        // Get error logs count
        response.setErrorLogsCount(auditLogRepository.countBySeverity(AuditLog.LogSeverity.ERROR));
        
        return response;
    }
}