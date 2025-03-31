package it.polito.cloudresources.be.dto.webhooks;

import it.polito.cloudresources.be.model.WebhookEventType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Data structure for webhook payload
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayload {
    private WebhookEventType eventType;
    private ZonedDateTime timestamp;
    private String webhookId;
    private Object data;
}