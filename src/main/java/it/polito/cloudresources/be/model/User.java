package it.polito.cloudresources.be.model;

import it.polito.cloudresources.be.config.DateTimeConfig;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;


/**
 * User entity representing system users
 * Keycloak will handle authentication, but we maintain a local copy
 * of users for relationship mapping and quick access to user details
 */
@Entity
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "email"),
                @UniqueConstraint(columnNames = "keycloak_id"),
                @UniqueConstraint(columnNames = "username")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    private String username;

    @NotBlank
    @Size(max = 50)
    private String firstName;

    @NotBlank
    @Size(max = 50)
    private String lastName;

    @NotBlank
    @Size(max = 100)
    @Email
    private String email;

    @Size(max = 5)
    private String avatar;

    @NotBlank
    @Column(name = "keycloak_id")
    private String keycloakId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    private Set<String> roles = new HashSet<>();

    @CreatedDate
    private ZonedDateTime createdAt;

    @LastModifiedDate
    private ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<Event> events = new HashSet<>();
    
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
    
    /**
     * Get full name from firstName and lastName
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }
}