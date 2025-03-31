package it.polito.cloudresources.be.dto.webhooks;

import it.polito.cloudresources.be.model.WebhookLog;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.domain.Page;

/**
 * Response DTO for paginated webhook logs
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookLogsResponseDTO {
    private Page<WebhookLog> logs;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private boolean hasNext;
    private boolean hasPrevious;
    
    /**
     * Create response from Page object
     */
    public WebhookLogsResponseDTO(Page<WebhookLog> page) {
        this.logs = page;
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.currentPage = page.getNumber();
        this.hasNext = page.hasNext();
        this.hasPrevious = page.hasPrevious();
    }
}