package it.polito.cloudresources.be.dto;


import it.polito.cloudresources.be.model.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Notification data transfer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {
    private Long id;

    @NotBlank(message = "Message is required")
    @Size(max = 500, message = "Message cannot exceed 500 characters")
    private String message;

    private NotificationType type = NotificationType.INFO;

    private boolean read;

    private LocalDateTime createdAt;
}
