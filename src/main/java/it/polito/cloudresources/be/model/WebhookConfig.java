package it.polito.cloudresources.be.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Entity for storing webhook configurations
 */
@Entity
@Table(name = "webhook_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WebhookConfig extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Size(max = 100)
    private String name;
    
    @NotBlank
    @Size(max = 255)
    private String url;
    
    @Enumerated(EnumType.STRING)
    private WebhookEventType eventType;
    
    // Secret will be hashed before storing
    @Size(max = 255)
    private String secret;
    
    private boolean enabled = true;
    
    private String federationId;
    // A webhook can be associated with a specific resource, resource type, or a group of resources
    
    // If not null, webhook applies to this specific resource only
    @ManyToOne
    @JoinColumn(name = "resource_id")
    private Resource resource;
    
    // If not null, webhook applies to this resource type
    @ManyToOne
    @JoinColumn(name = "resource_type_id")
    private ResourceType resourceType;
    
    // If true, webhook applies to all sub-resources of the specified resource
    private boolean includeSubResources = false;
    
    // Retry configuration
    private int maxRetries = 3;
    private int retryDelaySeconds = 60;
}