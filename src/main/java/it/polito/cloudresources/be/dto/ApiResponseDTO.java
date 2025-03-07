package it.polito.cloudresources.be.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDTO {
    private Boolean success;
    private String message;
    private Object data;

    public ApiResponseDTO(Boolean success, String message) {
        this.success = success;
        this.message = message;
        this.data = null;
    }
}