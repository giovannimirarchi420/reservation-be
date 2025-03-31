package it.polito.cloudresources.be.dto.webhooks;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for webhook creation that includes the client secret key
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookConfigResponseDTO {
    private WebhookConfigDTO webhook;
    private String clientSecret;
}