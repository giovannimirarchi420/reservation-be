package it.polito.cloudresources.be.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO for User data transfer, now based entirely on Keycloak
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private String id;

    @NotBlank(message = "Username is required")
    @Size(max = 50, message = "Username cannot exceed 50 characters")
    private String username;

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String avatar;
    
    private String sshPublicKey;

    private Set<String> roles;
    
    /**
     * Get full name from firstName and lastName
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }
}