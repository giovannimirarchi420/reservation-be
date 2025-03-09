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
import java.util.HashSet;
import java.util.Set;

/**
 * Resource type entity representing categories of resources (Server, GPU, Switch, etc.)
 */
@Entity
@Table(name = "resource_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ResourceType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    private String name;

    @Size(max = 7)
    private String color;

    @CreatedDate
    private ZonedDateTime createdAt;

    @LastModifiedDate
    private ZonedDateTime updatedAt;

    @OneToMany(mappedBy = "type", cascade = CascadeType.ALL)
    private Set<Resource> resources = new HashSet<>();
    
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