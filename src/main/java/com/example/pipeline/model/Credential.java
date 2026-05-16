package com.example.pipeline.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Secure storage for pipeline credentials (encrypted in database).
 */
@Entity
@Table(name = "credentials")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Credential {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Logical name of the credential (e.g., "s3-prod", "db-warehouse")
     */
    @Column(nullable = false, unique = true)
    private String name;
    
    /**
     * Type of credential (e.g., "s3", "database", "api")
     */
    @Column(nullable = false)
    private String type;
    
    /**
     * Encrypted credential value (plaintext stored encrypted here)
     */
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String encryptedValue;
    
    /**
     * Description for audit purposes
     */
    private String description;
    
    /**
     * When this credential was created
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * When this credential was last updated
     */
    private LocalDateTime updatedAt;
    
    /**
     * When this credential was last accessed (for audit logging)
     */
    private LocalDateTime lastAccessedAt;
    
    /**
     * Whether this credential is active/enabled
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    /**
     * Who created this credential (for audit)
     */
    private String createdBy;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
