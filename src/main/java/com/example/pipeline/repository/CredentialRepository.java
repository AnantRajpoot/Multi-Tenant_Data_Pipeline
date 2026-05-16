package com.example.pipeline.repository;

import com.example.pipeline.model.Credential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for persisting encrypted credentials.
 */
@Repository
public interface CredentialRepository extends JpaRepository<Credential, Long> {
    
    /**
     * Find credential by name.
     */
    Optional<Credential> findByName(String name);
    
    /**
     * Find active credentials by type.
     */
    java.util.List<Credential> findByTypeAndActive(String type, Boolean active);
    
    /**
     * Check if credential name exists.
     */
    boolean existsByName(String name);
}
