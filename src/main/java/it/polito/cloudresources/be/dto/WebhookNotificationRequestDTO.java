package it.polito.cloudresources.be.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for webhook notification requests
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookNotificationRequestDTO {
    
    @NotBlank(message = "Webhook ID is required")
    private String webhookId;
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Message is required")
    @Size(max = 500, message = "Message cannot exceed 500 characters")
    private String message;
    
    @Size(max = 50, message = "Type cannot exceed 50 characters")
    private String type = "INFO";
    
    private String eventId;
    private String resourceId;
    private String eventType;
    private Object metadata;
}
