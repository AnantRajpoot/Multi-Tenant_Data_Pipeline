package com.example.pipeline.service.credential;

import com.example.pipeline.model.Credential;

import java.util.Optional;

/**
 * Interface for managing pipeline credentials securely.
 * Implementations handle encryption/decryption and storage.
 */
public interface CredentialManager {
    
    /**
     * Store a new credential with encryption.
     * @param name logical name of the credential
     * @param type credential type (e.g., "s3", "database")
     * @param plaintext the actual credential value to encrypt
     * @param description optional description for audit
     * @return the stored Credential entity
     */
    Credential storeCredential(String name, String type, String plaintext, String description);
    
    /**
     * Retrieve and decrypt a credential by name.
     * @param name logical name of the credential
     * @return decrypted credential value, or empty if not found
     */
    Optional<String> getCredential(String name);
    
    /**
     * Update an existing credential (re-encrypt with new value).
     * @param name logical name of the credential
     * @param newPlaintext new credential value to encrypt
     */
    void updateCredential(String name, String newPlaintext);
    
    /**
     * Delete a credential.
     * @param name logical name of the credential
     */
    void deleteCredential(String name);
    
    /**
     * Check if a credential exists and is active.
     * @param name logical name of the credential
     */
    boolean credentialExists(String name);
    
    /**
     * List all credential names (without exposing values).
     */
    java.util.List<String> listCredentialNames();
}
