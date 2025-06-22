package it.polito.cloudresources.be.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

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
@EqualsAndHashCode(callSuper = true)
public class ResourceType extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 50)
    private String name;

    @Size(max = 7)
    private String color;

    @OneToMany(mappedBy = "type", cascade = CascadeType.ALL)
    @JsonIgnore
    private Set<Resource> resources = new HashSet<>();

    @Column(name = "site_id")
    @NotBlank
    private String siteId; // Keycloak Group ID representing the site

    // Custom toString to avoid lazy loading issues
    @Override
    public String toString() {
        return "ResourceType{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", color='" + color + '\'' +
               ", siteId='" + siteId + '\'' +
               '}';
    }
}