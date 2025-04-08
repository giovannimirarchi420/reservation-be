package it.polito.cloudresources.be.dto.webhooks;

import it.polito.cloudresources.be.model.WebhookEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for webhook configuration data transfer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookConfigDTO {
    private Long id;
    
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;
    
    @NotBlank(message = "URL is required")
    @Size(max = 255, message = "URL cannot exceed 255 characters")
    private String url;
    
    private WebhookEventType eventType = WebhookEventType.ALL;
    
    private boolean enabled = true;
    
    // Resource information
    private Long resourceId;
    private String resourceName;
    
    @NotBlank(message = "Site ID is required")
    private String siteId;
    private String siteName;
    
    // Resource type information
    private Long resourceTypeId;
    private String resourceTypeName;
    
    private boolean includeSubResources = false;
    
    // Retry configuration
    private int maxRetries = 3;
    private int retryDelaySeconds = 60;
}