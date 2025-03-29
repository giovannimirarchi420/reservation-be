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
        auditLog.setUserId(dto.getUserId());
        auditLog.setUsername(dto.getUsername());
        auditLog.setFederationId(dto.getFederationId());
        auditLog.setFederationName(dto.getFederationName());
        auditLog.setLogType(dto.getLogType());
        auditLog.setEntityType(dto.getEntityType());
        auditLog.setAction(dto.getAction());
        auditLog.setEntityId(dto.getEntityId());
        auditLog.setIpAddress(dto.getIpAddress());
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
        dto.setUserId(entity.getUserId());
        dto.setUsername(entity.getUsername());
        dto.setFederationId(entity.getFederationId());
        dto.setFederationName(entity.getFederationName());
        dto.setLogType(entity.getLogType());
        dto.setEntityType(entity.getEntityType());
        dto.setAction(entity.getAction());
        dto.setEntityId(entity.getEntityId());
        dto.setIpAddress(entity.getIpAddress());
        dto.setDetails(entity.getDetails());
        dto.setSeverity(entity.getSeverity());

        return dto;
    }
}