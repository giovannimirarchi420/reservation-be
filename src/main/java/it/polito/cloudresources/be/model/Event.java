package it.polito.cloudresources.be.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

import it.polito.cloudresources.be.config.datetime.DateTimeConfig;

/**
 * Event entity representing resource bookings
 * Now using Keycloak ID instead of User entity
 */
@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Event extends AuditableEntity {
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

    @NotBlank
    @Column(name = "keycloak_id")
    private String keycloakId; // Keycloak user ID instead of User entity reference
    
    // User name accessor methods for backward compatibility
    @Transient
    private String userName; // Not stored in DB, populated from Keycloak
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    /**
     * Pre-persist hook to ensure start and end dates have correct timezone
     */
    @PrePersist
    @Override
    public void prePersist() {
        // Call the parent class method first
        super.prePersist();
        
        // Ensure start and end dates have the application's default time zone
        if (start != null) {
            start = start.withZoneSameInstant(DateTimeConfig.DEFAULT_ZONE_ID);
        }
        if (end != null) {
            end = end.withZoneSameInstant(DateTimeConfig.DEFAULT_ZONE_ID);
        }
    }
    
    /**
     * Pre-update hook to ensure start and end dates have correct timezone
     */
    @PreUpdate
    @Override
    public void preUpdate() {
        // Call the parent class method first
        super.preUpdate();
        
        // Ensure start and end dates have the application's default time zone
        if (start != null) {
            start = start.withZoneSameInstant(DateTimeConfig.DEFAULT_ZONE_ID);
        }
        if (end != null) {
            end = end.withZoneSameInstant(DateTimeConfig.DEFAULT_ZONE_ID);
        }
    }
}