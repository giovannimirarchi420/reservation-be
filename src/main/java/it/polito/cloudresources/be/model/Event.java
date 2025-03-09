package it.polito.cloudresources.be.model;

import it.polito.cloudresources.be.config.DateTimeConfig;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;

/**
 * Event entity representing resource bookings
 */
@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String title;

    @Size(max = 500)
    private String description;

    @NotNull
    @Column(name = "start_time")
    private ZonedDateTime start;

    @NotNull
    @Column(name = "end_time")
    private ZonedDateTime end;

    @ManyToOne
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreatedDate
    private ZonedDateTime createdAt;

    @LastModifiedDate
    private ZonedDateTime updatedAt;
    
    /**
     * Pre-persist hook to set default time zone if none provided
     */
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);
        }
        if (updatedAt == null) {
            updatedAt = ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);
        }
        
        // Ensure start and end dates have the application's default time zone
        if (start != null) {
            start = start.withZoneSameInstant(DateTimeConfig.DEFAULT_ZONE_ID);
        }
        if (end != null) {
            end = end.withZoneSameInstant(DateTimeConfig.DEFAULT_ZONE_ID);
        }
    }
    
    /**
     * Pre-update hook to set update time
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = ZonedDateTime.now(DateTimeConfig.DEFAULT_ZONE_ID);
        
        // Ensure start and end dates have the application's default time zone
        if (start != null) {
            start = start.withZoneSameInstant(DateTimeConfig.DEFAULT_ZONE_ID);
        }
        if (end != null) {
            end = end.withZoneSameInstant(DateTimeConfig.DEFAULT_ZONE_ID);
        }
    }
}