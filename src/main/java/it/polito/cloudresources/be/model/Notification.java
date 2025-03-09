package it.polito.cloudresources.be.model;

import it.polito.cloudresources.be.config.DateTimeConfig;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;

/**
 * Notification entity for storing system notifications to users
 */
@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    private NotificationType type = NotificationType.INFO;

    private boolean read = false;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreatedDate
    private ZonedDateTime createdAt;

    @LastModifiedDate
    private ZonedDateTime updatedAt;
    
    /**
     * Pre-persist hook to set default time zone
     */
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);
        }
        if (updatedAt == null) {
            updatedAt = ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);
        }
    }
    
    /**
     * Pre-update hook to set update time
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);
    }
}