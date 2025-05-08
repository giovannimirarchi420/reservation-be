package it.polito.cloudresources.be.dto.logs;

import it.polito.cloudresources.be.dto.AuditLogDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Enhanced DTO for audit log responses with additional statistics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedAuditLogResponseDTO {
    private List<AuditLogDTO> logs;
    
    // Statistics
    private long totalElements;
    private long adminLogsCount;
    private long userLogsCount;
    private long errorLogsCount;
    
    public EnhancedAuditLogResponseDTO(List<AuditLogDTO> logs) {
        this.logs = logs;
    }
}