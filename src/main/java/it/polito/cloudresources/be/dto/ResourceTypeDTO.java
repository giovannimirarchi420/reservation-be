package it.polito.cloudresources.be.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for ResourceType data transfer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceTypeDTO {
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 50, message = "Name cannot exceed 50 characters")
    private String name;

    @Size(max = 7, message = "Color should be in hex format (e.g. #FF5733)")
    private String color;
}
