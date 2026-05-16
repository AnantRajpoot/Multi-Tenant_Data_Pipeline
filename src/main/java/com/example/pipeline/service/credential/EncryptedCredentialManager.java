package com.example.pipeline.service.credential;

import com.example.pipeline.model.Credential;
import com.example.pipeline.repository.CredentialRepository;
import org.jasypt.util.text.AES256TextEncryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of CredentialManager using Jasypt AES256 encryption.
 * Credentials are encrypted before storage and decrypted on retrieval.
 */
@Service
public class EncryptedCredentialManager implements CredentialManager {
    
    private final CredentialRepository credentialRepository;
    private final AES256TextEncryptor encryptor;
    
    @Autowired
    public EncryptedCredentialManager(CredentialRepository credentialRepository,
                                     @Value("${jasypt.encryptor.password:default-password}") String encryptionPassword) {
        this.credentialRepository = credentialRepository;
        this.encryptor = new AES256TextEncryptor();
        this.encryptor.setPassword(encryptionPassword);
    }
    
    @Override
    public Credential storeCredential(String name, String type, String plaintext, String description) {
        // Check if credential with this name already exists
        if (credentialRepository.existsByName(name)) {
            throw new IllegalArgumentException("Credential with name '" + name + "' already exists");
        }
        
        String encrypted = encryptor.encrypt(plaintext);
        
        Credential credential = Credential.builder()
                .name(name)
                .type(type)
                .encryptedValue(encrypted)
                .description(description)
                .active(true)
                .build();
        
        return credentialRepository.save(credential);
    }
    
    @Override
    public Optional<String> getCredential(String name) {
        Optional<Credential> credential = credentialRepository.findByName(name);
        
        if (credential.isPresent() && credential.get().getActive()) {
            try {
                // Update last accessed time for audit logging
                Credential cred = credential.get();
                cred.setLastAccessedAt(LocalDateTime.now());
                credentialRepository.save(cred);
                
                // Decrypt and return
                String decrypted = encryptor.decrypt(cred.getEncryptedValue());
                return Optional.of(decrypted);
            } catch (Exception e) {
                throw new RuntimeException("Failed to decrypt credential '" + name + "': " + e.getMessage());
            }
        }
        
        return Optional.empty();
    }
    
    @Override
    public void updateCredential(String name, String newPlaintext) {
        Credential credential = credentialRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Credential '" + name + "' not found"));
        
        String encrypted = encryptor.encrypt(newPlaintext);
        credential.setEncryptedValue(encrypted);
        credential.setUpdatedAt(LocalDateTime.now());
        
        credentialRepository.save(credential);
    }
    
    @Override
    public void deleteCredential(String name) {
        Credential credential = credentialRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Credential '" + name + "' not found"));
        
        credentialRepository.delete(credential);
    }
    
    @Override
    public boolean credentialExists(String name) {
        return credentialRepository.findByName(name)
                .map(Credential::getActive)
                .orElse(false);
    }
    
    @Override
    public List<String> listCredentialNames() {
        return credentialRepository.findAll().stream()
                .filter(Credential::getActive)
                .map(Credential::getName)
                .collect(Collectors.toList());
    }
}
