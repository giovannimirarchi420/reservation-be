package it.polito.cloudresources.be.dto;

import java.util.List;

import it.polito.cloudresources.be.model.ResourceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Resource data transfer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDTO {
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;
    
    private Long parentId;
    private List<Long> subResourceIds;
    private String parentName;

    @NotBlank(message = "Specifications are required")
    @Size(max = 255, message = "Specifications cannot exceed 255 characters")
    private String specs;

    @NotBlank(message = "Location is required")
    @Size(max = 100, message = "Location cannot exceed 100 characters")
    private String location;

    private ResourceStatus status = ResourceStatus.ACTIVE;

    @NotNull(message = "Resource type is required")
    private Long typeId;

    // Additional fields for the frontend
    private String typeName;
    private String typeColor;
}