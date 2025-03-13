package it.polito.cloudresources.be.model;

import it.polito.cloudresources.be.config.DateTimeConfig;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;

/**
 * Base class for auditable entities that need createdAt and updatedAt fields
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class AuditableEntity {
    
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