package com.example.pipeline.config;

import com.example.pipeline.service.credential.CredentialManager;
import com.example.pipeline.util.EnvVarResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * Configuration to initialize the credential manager with the EnvVarResolver.
 * This allows ${CRED_NAME} syntax to resolve encrypted credentials from the database.
 */
@Configuration
public class CredentialInitializer {
    
    @Autowired(required = false)
    private CredentialManager credentialManager;
    
    @PostConstruct
    public void initialize() {
        if (credentialManager != null) {
            EnvVarResolver.setCredentialManager(credentialManager);
        }
    }
}
