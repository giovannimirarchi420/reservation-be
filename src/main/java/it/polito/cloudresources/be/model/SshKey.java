package it.polito.cloudresources.be.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity for storing SSH keys in the database
 */
@Entity
@Table(name = "ssh_keys")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SshKey {
    
    @Id
    @GeneratedValue
    private Long id;
    
    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;
    
    @Column(name = "ssh_key", nullable = false, length = 4000)
    private String sshKey;
    
    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;
    
    @Column(name = "created_by", nullable = false)
    private String createdBy;
    
    @Column(name = "updated_by", nullable = false)
    private String updatedBy;
}
