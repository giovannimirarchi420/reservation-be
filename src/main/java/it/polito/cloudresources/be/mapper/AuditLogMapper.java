package it.polito.cloudresources.be.mapper;

import it.polito.cloudresources.be.dto.AuditLogDTO;
import it.polito.cloudresources.be.model.AuditLog;
import org.springframework.stereotype.Component;

/**
 * Mapper for converting between AuditLog and AuditLogDTO objects
 */
@Component
public class AuditLogMapper implements EntityMapper<AuditLogDTO, AuditLog> {

    @Override
    public AuditLog toEntity(AuditLogDTO dto) {
        if (dto == null) {
            return null;
        }

        AuditLog auditLog = new AuditLog();
        auditLog.setId(dto.getId());
        auditLog.setTimestamp(dto.getTimestamp());
        auditLog.setUsername(dto.getUsername());
        auditLog.setSiteName(dto.getSiteName());
        auditLog.setLogType(dto.getLogType());
        auditLog.setEntityType(dto.getEntityType());
        auditLog.setAction(dto.getAction());
        auditLog.setEntityId(dto.getEntityId());
        auditLog.setDetails(dto.getDetails());
        auditLog.setSeverity(dto.getSeverity());

        return auditLog;
    }

    @Override
    public AuditLogDTO toDto(AuditLog entity) {
        if (entity == null) {
            return null;
        }

        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(entity.getId());
        dto.setTimestamp(entity.getTimestamp());
        dto.setUsername(entity.getUsername());
        dto.setSiteName(entity.getSiteName());
        dto.setLogType(entity.getLogType());
        dto.setEntityType(entity.getEntityType());
        dto.setAction(entity.getAction());
        dto.setEntityId(entity.getEntityId());
        dto.setDetails(entity.getDetails());
        dto.setSeverity(entity.getSeverity());

        return dto;
    }
}