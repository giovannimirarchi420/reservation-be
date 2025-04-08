package it.polito.cloudresources.be.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Entity for storing application audit logs in the database
 */
@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp", nullable = false)
    private ZonedDateTime timestamp;

    @Column(name = "username")
    private String username;

    @Column(name = "site_name")
    private String siteName;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false, length = 50)
    private AuditLog.LogType logType;

    @Column(name = "details")
    private String details;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditLog.LogAction action;

    @Column(name = "entity_id")
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private LogSeverity severity;

    /**
     * Enumeration for log severity levels
     */
    public enum LogSeverity {
        INFO,
        WARNING,
        ERROR
    }

    public enum LogAction {
        CREATE,
        DELETE,
        UPDATE
    }

    public enum LogType {
        USER,
        ADMIN,
    }

    @Data
    @AllArgsConstructor
    public static class LogEntity {
        private String entityType;
        private String entityId;
    }
}